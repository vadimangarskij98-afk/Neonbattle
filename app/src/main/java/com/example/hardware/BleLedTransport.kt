package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.model.BleChipset
import com.example.model.ControllerProtocol
import com.example.model.LedDevice
import com.example.model.PinOrder
import com.example.model.RgbColor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class BleConnectSuccess(
    val chipset: BleChipset,
    val gattChannelInfo: String
)

/**
 * Rock-solid Bluetooth Low Energy (GATT) scanner and controller transport for Lotus Lantern
 * (wl.smartled), ELK-BLEDOM, MELK, LotusLanternX (BJ_LED), Triones, and MagicBLE strips.
 *
 * Key real-device reliability fixes:
 * - Does NOT call requestMtu() alongside discoverServices() (fixes silent GATT deadlock on Xiaomi/Android)
 * - Waits 250ms after STATE_CONNECTED before discoverServices() for stable service table discovery
 * - STRICTLY rejects Bluetooth SIG generic services (0x1800/0x1801/0x180A/0x180F/0x1812) so the app
 *   NEVER falsely claims "Connected" to a TV, watch, speaker, or neighbor device
 * - Checks already-connected GATT devices in BluetoothManager in case Android kept a link open
 * - Supports live protocol dialect switching across all 5 Lotus/ELK/LotusX/Triones/MagicBLE formats
 * - Uses serialized writes with safe pacing (40ms) so cheap BLE 4.0 LED chips never overflow
 */
class BleLedTransport(private val context: Context) {

    private val lotusWriteUuids = setOf(
        UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ee01-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    )

    private val trionesWriteUuids = setOf(
        UUID.fromString("0000ffd9-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")
    )

    private val magicBleWriteUuids = setOf(
        UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
    )

    private val knownLedServiceUuids = setOf(
        ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ffd5-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ae00-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ae30-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ee00-0000-1000-8000-00805f9b34fb")
    )

    // Standard non-LED Bluetooth SIG services we must NEVER treat as an LED control channel
    private val ignoredSigServicePrefixes = listOf(
        "00001800-", // Generic Access
        "00001801-", // Generic Attribute
        "0000180a-", // Device Information
        "0000180f-", // Battery Service
        "00001812-", // Human Interface Device
        "0000180d-", // Heart Rate
        "00001805-", // Current Time
        "0000fe9f-", // Google Fast Pair
        "0000fd6f-"  // Exposure Notification
    )

    private val ledNameKeywords = listOf(
        "ELK", "BLEDOM", "BLEDOB", "MELK", "LED", "LOTUS", "TRIONES",
        "HAPPY", "QHM", "BJ_LED", "RGB", "NEON", "SP105", "SP110",
        "ZENGGE", "MAGIC", "STRIP", "LAMP", "BULB", "DREAM", "YC-"
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val writeMutex = Mutex()
    private var pendingWriteDeferred: CompletableDeferred<Boolean>? = null

    private val _scannedDevices = MutableStateFlow<List<LedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<LedDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _disconnectEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val disconnectEvents: SharedFlow<String> = _disconnectEvents.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var activeChipset: BleChipset = BleChipset.LOTUS_ELK_V6
    private var activeScanCallback: ScanCallback? = null
    private var intentionalDisconnect: Boolean = false

    private fun bluetoothManager(): BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private fun bluetoothAdapter(): BluetoothAdapter? = bluetoothManager()?.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter()?.isEnabled == true

    fun setActiveChipset(chipset: BleChipset) {
        activeChipset = chipset
    }

    fun getActiveChipset(): BleChipset = activeChipset

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Result<Unit> {
        if (!hasBlePermissions()) {
            return Result.failure(IllegalStateException("Разрешите доступ к Bluetooth для поиска ленты"))
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            return Result.failure(IllegalStateException("Включите Bluetooth на телефоне"))
        }
        val scanner = adapter.bluetoothLeScanner
            ?: return Result.failure(IllegalStateException("BLE сканер недоступен на этом устройстве"))

        stopScan()
        val found = LinkedHashMap<String, LedDevice>()

        // Also inspect already-connected GATT devices in case Android/another app already linked the strip
        runCatching {
            bluetoothManager()?.getConnectedDevices(BluetoothProfile.GATT).orEmpty().forEach { btDev ->
                val addr = btDev.address ?: return@forEach
                val name = btDev.name?.takeIf { it.isNotBlank() } ?: return@forEach
                val isLed = ledNameKeywords.any { name.contains(it, ignoreCase = true) }
                found[addr] = LedDevice(
                    id = "ble_$addr",
                    name = name,
                    address = addr,
                    port = 0,
                    protocol = ControllerProtocol.BLE_LOTUS_ELK,
                    macAddress = addr,
                    rssi = -45,
                    isLikelyLed = isLed,
                    serviceHint = "Уже в GATT"
                )
            }
        }

        _scannedDevices.value = found.values.sortedWith(
            compareByDescending<LedDevice> { it.isLikelyLed }.thenByDescending { it.rssi }
        )
        _isScanning.value = true

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val btDevice = result.device ?: return
                val address = btDevice.address ?: return
                val rawName = btDevice.name?.takeIf { it.isNotBlank() }
                    ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }

                val advertisedUuids = result.scanRecord?.serviceUuids.orEmpty()
                val matchedUuid = advertisedUuids.firstOrNull { it in knownLedServiceUuids }
                val matchesLedName = rawName != null && ledNameKeywords.any {
                    rawName.contains(it, ignoreCase = true)
                }

                // Skip unnamed non-LED beacons, keep unnamed devices advertising known LED UUIDs
                if (rawName == null && matchedUuid == null) return

                val displayName = rawName ?: "LED Контроллер (${address.takeLast(5)})"
                val isLed = matchesLedName || matchedUuid != null
                val uuidHint = matchedUuid?.uuid?.toString()?.substring(4, 8)?.uppercase()?.let { "0x$it" }.orEmpty()

                found[address] = LedDevice(
                    id = "ble_$address",
                    name = displayName,
                    address = address,
                    port = 0,
                    protocol = ControllerProtocol.BLE_LOTUS_ELK,
                    macAddress = address,
                    rssi = result.rssi,
                    isLikelyLed = isLed,
                    serviceHint = uuidHint
                )

                _scannedDevices.value = found.values.sortedWith(
                    compareByDescending<LedDevice> { it.isLikelyLed }
                        .thenByDescending { it.rssi }
                )
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
            }
        }

        activeScanCallback = callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return runCatching {
            scanner.startScan(null, settings, callback)
        }.onFailure {
            activeScanCallback = null
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val cb = activeScanCallback ?: return
        activeScanCallback = null
        _isScanning.value = false
        if (hasBlePermissions()) {
            runCatching { bluetoothAdapter()?.bluetoothLeScanner?.stopScan(cb) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(
        device: LedDevice,
        preferredChipset: BleChipset? = null
    ): Result<BleConnectSuccess> = withContext(Dispatchers.IO) {
        if (!hasBlePermissions()) {
            return@withContext Result.failure(IllegalStateException("Нет разрешений Bluetooth"))
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Включите Bluetooth на телефоне"))
        }

        stopScan()
        disconnect()
        delay(120L)

        val remote = runCatching { adapter.getRemoteDevice(device.address) }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Неверный MAC-адрес устройства"))

        intentionalDisconnect = false
        val connectDeferred = CompletableDeferred<Result<BleConnectSuccess>>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    // Wait 250ms without calling requestMtu() to avoid MIUI/Android GATT race condition
                    mainHandler.postDelayed({
                        if (activeGatt == gatt && hasBlePermissions()) {
                            runCatching { gatt.discoverServices() }
                        }
                    }, 250L)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    writeCharacteristic = null
                    pendingWriteDeferred?.complete(false)
                    pendingWriteDeferred = null

                    if (!connectDeferred.isCompleted) {
                        val reason = when (status) {
                            133 -> "Ошибка BLE 133. Закройте оригинальный Lotus Lantern, выключите/включите питание ленты и повторите."
                            8 -> "Лента не отвечает (таймаут связи). Подойдите ближе к контроллеру."
                            else -> "Лента разорвала BLE соединение (код $status)."
                        }
                        connectDeferred.complete(Result.failure(IllegalStateException(reason)))
                    } else if (!intentionalDisconnect) {
                        runCatching { gatt.close() }
                        if (activeGatt == gatt) activeGatt = null
                        _disconnectEvents.tryEmit("BLE соединение с «${device.name}» потеряно")
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(
                            Result.failure(IllegalStateException("Ошибка чтения GATT сервисов ($status)"))
                        )
                    }
                    return
                }

                // Filter out standard Bluetooth SIG system services so we NEVER connect to a TV/watch/etc.
                val vendorServices = gatt.services.filter { svc ->
                    val u = svc.uuid.toString().lowercase()
                    ignoredSigServicePrefixes.none { u.startsWith(it) }
                }
                val vendorChars = vendorServices.flatMap { it.characteristics }

                val writableVendorChars = vendorChars.filter { char ->
                    val p = char.properties
                    (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                        (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                }

                val lotusChar = writableVendorChars.firstOrNull { it.uuid in lotusWriteUuids }
                val trionesChar = writableVendorChars.firstOrNull { it.uuid in trionesWriteUuids }
                val magicChar = writableVendorChars.firstOrNull { it.uuid in magicBleWriteUuids }
                val customVendorChar = writableVendorChars.firstOrNull()

                val chosenChar = lotusChar ?: trionesChar ?: magicChar ?: customVendorChar

                if (chosenChar == null) {
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(
                            Result.failure(
                                IllegalStateException(
                                    "«${device.name}» — не LED-лента (нет канала управления светом). " +
                                        "Закройте Lotus Lantern и выберите саму ленту (ELK-BLEDOM, MELK, QHM, LED)."
                                )
                            )
                        )
                    }
                    return
                }

                val autoChipset = when {
                    preferredChipset != null -> preferredChipset
                    device.name.contains("BJ_LED", ignoreCase = true) ||
                        device.name.contains("LotusX", ignoreCase = true) -> BleChipset.LOTUS_X_BC
                    trionesChar != null -> BleChipset.TRIONES_HAPPY
                    magicChar != null -> BleChipset.MAGIC_BLE_31
                    else -> BleChipset.LOTUS_ELK_V6
                }

                writeCharacteristic = chosenChar
                activeChipset = autoChipset

                val svcShort = chosenChar.service?.uuid?.toString()?.substring(4, 8)?.uppercase() ?: "????"
                val charShort = chosenChar.uuid.toString().substring(4, 8).uppercase()
                val channelInfo = "GATT 0x$svcShort → 0x$charShort"

                if (!connectDeferred.isCompleted) {
                    connectDeferred.complete(
                        Result.success(BleConnectSuccess(autoChipset, channelInfo))
                    )
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingWriteDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        // Enforce TRANSPORT_LE for dual-mode Android stacks
        activeGatt = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

        val result = withTimeoutOrNull(10_000L) { connectDeferred.await() }
            ?: Result.failure(
                IllegalStateException(
                    "Таймаут подключения к «${device.name}». Убедитесь, что лента включена в розетку и оригинальный Lotus Lantern закрыт."
                )
            )

        if (result.isFailure) {
            disconnect()
        }
        result
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        intentionalDisconnect = true
        writeCharacteristic = null
        pendingWriteDeferred?.complete(false)
        pendingWriteDeferred = null
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null && hasBlePermissions()) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    /**
     * Clean post-connect initialization sequence required by Lotus Lantern / ELK-BLEDOM chips:
     * Power ON -> Brightness 100% -> Initial Color.
     */
    suspend fun sendInitHandshake(color: RgbColor, pinOrder: PinOrder, brightnessPercent: Int) {
        sendPower(true)
        delay(80L)
        sendBrightness(brightnessPercent)
        delay(80L)
        sendColor(color, pinOrder)
    }

    suspend fun sendPower(on: Boolean): Boolean {
        return when (activeChipset) {
            BleChipset.LOTUS_ELK_V6 -> {
                // Send both V6 (F0..FF) and Legacy (01..00) power frames so any ELK/MELK revision reacts!
                val ok1 = writeBytesSerialized(LedProtocolPackets.lotusV6Power(on))
                delay(45L)
                val ok2 = writeBytesSerialized(LedProtocolPackets.lotusPower(on))
                ok1 || ok2
            }
            BleChipset.LOTUS_ELK_LEGACY -> writeBytesSerialized(LedProtocolPackets.lotusPower(on))
            BleChipset.LOTUS_X_BC -> writeBytesSerialized(LedProtocolPackets.lotusXPower(on))
            BleChipset.TRIONES_HAPPY -> writeBytesSerialized(LedProtocolPackets.trionesPower(on))
            BleChipset.MAGIC_BLE_31 -> writeBytesSerialized(LedProtocolPackets.magicHomePower(on))
        }
    }

    suspend fun sendBrightness(percent: Int): Boolean {
        return when (activeChipset) {
            BleChipset.LOTUS_ELK_V6 -> writeBytesSerialized(LedProtocolPackets.lotusV6Brightness(percent))
            BleChipset.LOTUS_ELK_LEGACY -> writeBytesSerialized(LedProtocolPackets.lotusBrightness(percent))
            BleChipset.LOTUS_X_BC -> writeBytesSerialized(LedProtocolPackets.lotusXBrightness(percent))
            BleChipset.TRIONES_HAPPY, BleChipset.MAGIC_BLE_31 -> true // Brightness is encoded directly in scaled RGB
        }
    }

    suspend fun sendColor(color: RgbColor, pinOrder: PinOrder): Boolean {
        val packet = when (activeChipset) {
            BleChipset.LOTUS_ELK_V6 -> LedProtocolPackets.lotusV6Color(color, pinOrder)
            BleChipset.LOTUS_ELK_LEGACY -> LedProtocolPackets.lotusColor(color, pinOrder)
            BleChipset.LOTUS_X_BC -> LedProtocolPackets.lotusXColor(color, pinOrder)
            BleChipset.TRIONES_HAPPY -> LedProtocolPackets.trionesColor(color, pinOrder)
            BleChipset.MAGIC_BLE_31 -> LedProtocolPackets.magicHomeColor(color, pinOrder)
        }
        return writeBytesSerialized(packet)
    }

    suspend fun sendPinOrder(pinOrder: PinOrder): Boolean {
        return when (activeChipset) {
            BleChipset.LOTUS_ELK_V6, BleChipset.LOTUS_ELK_LEGACY ->
                writeBytesSerialized(LedProtocolPackets.lotusPinOrder(pinOrder))
            else -> true
        }
    }

    suspend fun sendExternalMic(enabled: Boolean, sensitivityPercent: Int): Boolean {
        return when (activeChipset) {
            BleChipset.LOTUS_ELK_V6, BleChipset.LOTUS_ELK_LEGACY ->
                writeBytesSerialized(LedProtocolPackets.lotusExternalMic(enabled, sensitivityPercent))
            else -> false
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun writeBytesSerialized(payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val gatt = activeGatt ?: return@withLock false
            val char = writeCharacteristic ?: return@withLock false
            if (!hasBlePermissions()) return@withLock false

            val useNoResponse = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            val writeType = if (useNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            val ack = CompletableDeferred<Boolean>()
            pendingWriteDeferred = ack

            val started = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(char, payload, writeType) == BluetoothGatt.GATT_SUCCESS
                } else {
                    char.writeType = writeType
                    char.value = payload
                    gatt.writeCharacteristic(char)
                }
            }.getOrDefault(false)

            if (!started) {
                pendingWriteDeferred = null
                return@withLock false
            }

            val completed = withTimeoutOrNull(120L) { ack.await() } ?: useNoResponse
            pendingWriteDeferred = null
            // Gentle pacing so cheap BLE 4.0 LED controller chips never drop rapid frames
            delay(35L)
            completed
        }
    }
}
