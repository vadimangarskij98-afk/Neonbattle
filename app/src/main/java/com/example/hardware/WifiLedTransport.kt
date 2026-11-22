package com.example.hardware

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.example.model.ControllerProtocol
import com.example.model.LedDevice
import com.example.model.PinOrder
import com.example.model.RgbColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class WifiLedTransport(private val context: Context) {

    private val writeMutex = Mutex()
    private var activeDevice: LedDevice? = null
    private var tcpSocket: Socket? = null
    private var tcpOutput: OutputStream? = null
    private var udpSocket: DatagramSocket? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Real LAN UDP discovery for Magic Home / Flux Wi-Fi LED controllers (port 48899).
     */
    suspend fun discoverLanControllers(timeoutMs: Long = 2200L): List<LedDevice> =
        withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, LedDevice>()
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = runCatching {
                wifiManager?.createMulticastLock("neonflow_discovery")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()

            val socket = runCatching {
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 350
                }
            }.getOrNull() ?: return@withContext emptyList()

            try {
                val queryBytes = "HF-A11ASSISTHREAD".toByteArray(Charsets.US_ASCII)
                val broadcastTargets = resolveBroadcastAddresses()

                repeat(2) {
                    broadcastTargets.forEach { addr ->
                        runCatching {
                            val pkt = DatagramPacket(queryBytes, queryBytes.size, addr, 48899)
                            socket.send(pkt)
                        }
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val recvBuf = ByteArray(512)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(recvBuf, recvBuf.size)
                        socket.receive(packet)
                        val response = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (response == "HF-A11ASSISTHREAD" || response.isEmpty()) continue

                        val parts = response.split(",")
                        val ip = parts.getOrNull(0)?.trim().takeIf { !it.isNullOrBlank() }
                            ?: packet.address.hostAddress.orEmpty()
                        val mac = parts.getOrNull(1)?.trim().orEmpty()
                        val model = parts.getOrNull(2)?.trim().orEmpty()
                        if (ip.isNotBlank()) {
                            val id = "wifi_${ip}_5577"
                            val displayName = if (model.isNotBlank()) "LED $model" else "Wi-Fi Лента ($ip)"
                            found[id] = LedDevice(
                                id = id,
                                name = displayName,
                                address = ip,
                                port = 5577,
                                protocol = ControllerProtocol.WIFI_MAGIC_HOME,
                                macAddress = mac
                            )
                        }
                    } catch (_: SocketTimeoutException) {
                        // Keep listening until timeout
                    } catch (_: Exception) {
                        break
                    }
                }
            } finally {
                runCatching { socket.close() }
                runCatching {
                    if (multicastLock?.isHeld == true) multicastLock.release()
                }
            }
            found.values.toList()
        }

    suspend fun connect(device: LedDevice): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            closeInternal()
            val cleanHost = device.address.trim()
            if (cleanHost.isEmpty()) {
                return@withLock Result.failure(IllegalArgumentException("Укажите корректный IP-адрес"))
            }
            runCatching {
                when (device.protocol) {
                    ControllerProtocol.WIFI_MAGIC_HOME,
                    ControllerProtocol.WIFI_LOTUS_TCP -> {
                        val sock = Socket()
                        sock.tcpNoDelay = true
                        sock.connect(InetSocketAddress(cleanHost, device.port), 3000)
                        sock.soTimeout = 2000
                        tcpSocket = sock
                        tcpOutput = sock.getOutputStream()
                        activeDevice = device.copy(address = cleanHost)
                    }
                    ControllerProtocol.WIFI_LOTUS_UDP -> {
                        InetAddress.getByName(cleanHost)
                        udpSocket = DatagramSocket()
                        activeDevice = device.copy(address = cleanHost)
                    }
                    ControllerProtocol.WIFI_WLED -> {
                        val baseUrl = buildWledUrl(device.copy(address = cleanHost))
                        val req = Request.Builder().url("$baseUrl/json/info").get().build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                error("WLED ответил кодом ${resp.code}")
                            }
                        }
                        udpSocket = DatagramSocket()
                        activeDevice = device.copy(address = cleanHost)
                    }
                    ControllerProtocol.BLE_LOTUS_ELK -> {
                        error("Протокол BLE обслуживается Bluetooth транспортом")
                    }
                }
            }.onFailure {
                closeInternal()
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            closeInternal()
        }
    }

    suspend fun sendPower(on: Boolean): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val device = activeDevice ?: return@withLock false
            runCatching {
                when (device.protocol) {
                    ControllerProtocol.WIFI_MAGIC_HOME ->
                        writeTcp(device, LedProtocolPackets.magicHomePower(on))
                    ControllerProtocol.WIFI_LOTUS_TCP ->
                        writeTcp(device, LedProtocolPackets.lotusPower(on))
                    ControllerProtocol.WIFI_LOTUS_UDP ->
                        writeUdp(device.address, device.port, LedProtocolPackets.lotusPower(on))
                    ControllerProtocol.WIFI_WLED ->
                        postWledJson(device, """{"on":$on,"transition":4}""")
                    ControllerProtocol.BLE_LOTUS_ELK -> false
                }
            }.getOrDefault(false)
        }
    }

    suspend fun sendColorFrame(
        color: RgbColor,
        segments: List<RgbColor>,
        pinOrder: PinOrder,
        brightnessPercent: Int,
        smoothness: Float,
        isStreamingAnimation: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val device = activeDevice ?: return@withLock false
            runCatching {
                when (device.protocol) {
                    ControllerProtocol.WIFI_MAGIC_HOME ->
                        writeTcp(device, LedProtocolPackets.magicHomeColor(color, pinOrder))
                    ControllerProtocol.WIFI_LOTUS_TCP ->
                        writeTcp(device, LedProtocolPackets.lotusColor(color, pinOrder))
                    ControllerProtocol.WIFI_LOTUS_UDP ->
                        writeUdp(device.address, device.port, LedProtocolPackets.lotusColor(color, pinOrder))
                    ControllerProtocol.WIFI_WLED -> {
                        if (isStreamingAnimation) {
                            val drgb = LedProtocolPackets.wledDrgbFrame(segments, pinOrder, timeoutSec = 2)
                            writeUdp(device.address, 21324, drgb)
                        } else {
                            val transitionDs = (smoothness * 12f).toInt().coerceIn(0, 15)
                            val payload = LedProtocolPackets.wledStateJson(
                                on = true,
                                color = color,
                                pinOrder = pinOrder,
                                brightness = (brightnessPercent * 255 / 100).coerceIn(1, 255),
                                transitionDeciseconds = transitionDs
                            )
                            postWledJson(device, payload)
                        }
                    }
                    ControllerProtocol.BLE_LOTUS_ELK -> false
                }
            }.getOrDefault(false)
        }
    }

    suspend fun sendBuiltInMode(modeCode: Int, speedPercent: Int): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val device = activeDevice ?: return@withLock false
            runCatching {
                when (device.protocol) {
                    ControllerProtocol.WIFI_MAGIC_HOME ->
                        writeTcp(device, LedProtocolPackets.magicHomeBuiltInMode(modeCode, speedPercent))
                    ControllerProtocol.WIFI_LOTUS_TCP -> {
                        writeTcp(device, LedProtocolPackets.lotusBuiltInMode(modeCode))
                        writeTcp(device, LedProtocolPackets.lotusSpeed(speedPercent))
                    }
                    ControllerProtocol.WIFI_LOTUS_UDP -> {
                        writeUdp(device.address, device.port, LedProtocolPackets.lotusBuiltInMode(modeCode))
                        writeUdp(device.address, device.port, LedProtocolPackets.lotusSpeed(speedPercent))
                    }
                    else -> false
                }
            }.getOrDefault(false)
        }
    }

    /**
     * Channel order is remapped in software inside every colour packet, so nothing has to be sent
     * to the controller. Sending a hardware re-map as well would swap the channels twice.
     */
    @Suppress("UNUSED_PARAMETER")
    fun sendPinOrder(pinOrder: PinOrder): Boolean = true

    suspend fun sendExternalMic(enabled: Boolean, sensitivityPercent: Int): Boolean =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val device = activeDevice ?: return@withLock false
                runCatching {
                    when (device.protocol) {
                        ControllerProtocol.WIFI_LOTUS_TCP ->
                            writeTcp(device, LedProtocolPackets.lotusExternalMic(enabled, sensitivityPercent))
                        ControllerProtocol.WIFI_LOTUS_UDP ->
                            writeUdp(
                                device.address,
                                device.port,
                                LedProtocolPackets.lotusExternalMic(enabled, sensitivityPercent)
                            )
                        else -> false
                    }
                }.getOrDefault(false)
            }
        }

    private fun writeTcp(device: LedDevice, bytes: ByteArray): Boolean {
        return try {
            val out = ensureTcpConnected(device) ?: return false
            out.write(bytes)
            out.flush()
            true
        } catch (_: Exception) {
            closeTcpOnly()
            try {
                val out = ensureTcpConnected(device) ?: return false
                out.write(bytes)
                out.flush()
                true
            } catch (_: Exception) {
                closeTcpOnly()
                false
            }
        }
    }

    private fun ensureTcpConnected(device: LedDevice): OutputStream? {
        val current = tcpSocket
        if (current != null && current.isConnected && !current.isClosed) {
            return tcpOutput
        }
        val sock = Socket()
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(device.address, device.port), 2000)
        sock.soTimeout = 2000
        tcpSocket = sock
        tcpOutput = sock.getOutputStream()
        return tcpOutput
    }

    private fun writeUdp(host: String, port: Int, bytes: ByteArray): Boolean {
        val sock = udpSocket ?: DatagramSocket().also { udpSocket = it }
        val addr = InetAddress.getByName(host)
        val packet = DatagramPacket(bytes, bytes.size, addr, port)
        sock.send(packet)
        return true
    }

    private fun postWledJson(device: LedDevice, json: String): Boolean {
        val url = "${buildWledUrl(device)}/json/state"
        val req = Request.Builder()
            .url(url)
            .post(json.toRequestBody(jsonMediaType))
            .build()
        return httpClient.newCall(req).execute().use { it.isSuccessful }
    }

    private fun buildWledUrl(device: LedDevice): String {
        return if (device.port == 80 || device.port <= 0) {
            "http://${device.address}"
        } else {
            "http://${device.address}:${device.port}"
        }
    }

    private fun resolveBroadcastAddresses(): List<InetAddress> {
        val targets = LinkedHashSet<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                iface.interfaceAddresses.forEach { ifAddr ->
                    val bcast = ifAddr.broadcast
                    if (bcast is Inet4Address) {
                        targets.add(bcast)
                    }
                }
            }
        }
        return targets.toList()
    }

    private fun closeTcpOnly() {
        runCatching { tcpOutput?.close() }
        runCatching { tcpSocket?.close() }
        tcpOutput = null
        tcpSocket = null
    }

    private fun closeInternal() {
        closeTcpOnly()
        runCatching { udpSocket?.close() }
        udpSocket = null
        activeDevice = null
    }
}
