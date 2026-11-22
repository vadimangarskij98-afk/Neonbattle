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
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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

/**
 * Production-grade Bluetooth Low Energy (GATT) scanner and controller transport for direct
 * LED strip control when Wi-Fi is unavailable.
 *
 * Features:
 * - Discovers named LED controllers (ELK-BLEDOM, MELK, Lotus, Triones, HappyLighting, QHM, SP105E, BJ_LED)
 *   AND unnamed controllers advertising standard LED service UUIDs (0xFFF0, 0xFFD5, 0xAE00, 0xFFE0, 0xFFE5)
 * - Uses TRANSPORT_LE to eliminate Android GATT error 133 on dual-mode adapters
 * - Auto-detects chipset protocol (Lotus/ELK 7E..EF vs Triones/HappyLighting 56..AA)
 * - Serializes writes with Mutex + onCharacteristicWrite callback gate so packets never collide
 * - Emits disconnect events cleanly when the strip powers down or moves out of range
 */
fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

class BleLedTransport(private val context: Context) {

    private val lotusWriteUuids = setOf(
        UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    )

    private val trionesWriteUuids = setOf(
        UUID.fromString("0000ffd9-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")
    )

    private val knownLedServiceUuids = setOf(
        ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ffd5-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ae00-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
        ParcelUuid.fromString("0000ffe5-0000-1000-8000-00805f9b34fb")
    )

    private val ledNameKeywords = listOf(
        "ELK", "BLEDOM", "MELK", "LED", "LOTUS", "TRIONES",
        "HAPPY", "QHM", "BJ_LED", "RGB", "NEON", "SP105", "SP110", "ZENGGE", "MAGIC"
    )

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
    private var detectedChipset: BleChipset = BleChipset.LOTUS_ELK
    private var activeScanCallback: ScanCallback? = null
    @Volatile private var intentionalDisconnect: Boolean = false
    @Volatile private var isMelkFirmware: Boolean = false

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(true)
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter()?.isEnabled == true

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /** Runtime permissions the UI must request before scanning/connecting. */
    fun requiredPermissions(): Array<String> = requiredBlePermissions()

    @SuppressLint("MissingPermission")
    fun startScan(): Result<Unit> {
        if (!hasBlePermissions()) {
            return Result.failure(IllegalStateException("Разрешите доступ к Bluetooth для поиска ленты"))
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            return Result.failure(IllegalStateException("Включите Bluetooth на телефоне"))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
            return Result.failure(
                IllegalStateException("Включите геолокацию: на Android 11 и ниже без неё Bluetooth-лента не находится")
            )
        }
        val scanner = adapter.bluetoothLeScanner
            ?: return Result.failure(IllegalStateException("BLE сканер недоступен на этом устройстве"))

        stopScan()
        val found = LinkedHashMap<String, LedDevice>()
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val btDevice = result.device ?: return
                val address = btDevice.address ?: return
                val rawName = btDevice.name?.takeIf { it.isNotBlank() }
                    ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }

                val advertisedUuids = result.scanRecord?.serviceUuids.orEmpty()
                val matchesLedService = advertisedUuids.any { it in knownLedServiceUuids }
                val matchesLedName = rawName != null && ledNameKeywords.any {
                    rawName.contains(it, ignoreCase = true)
                }

                // Skip completely unnamed non-LED beacons to keep the list clean,
                // while ALWAYS keeping unnamed devices advertising LED service UUIDs
                if (rawName == null && !matchesLedService) return

                val displayName = rawName ?: "LED Контроллер (${address.takeLast(5)})"
                val isLed = matchesLedName || matchesLedService

                found[address] = LedDevice(
                    id = "ble_$address",
                    name = displayName,
                    address = address,
                    port = 0,
                    protocol = ControllerProtocol.BLE_LOTUS_ELK,
                    macAddress = address,
                    rssi = result.rssi,
                    isLikelyLed = isLed
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

    /**
     * Connects with one automatic retry: cheap ELK/Lotus controllers very often reject the first
     * attempt with GATT error 133 right after a scan, and succeed on the second.
     */
    suspend fun connect(device: LedDevice): Result<BleChipset> {
        var last: Result<BleChipset> = Result.failure(IllegalStateException("BLE не подключён"))
        repeat(3) { attempt ->
            last = connectOnce(device)
            if (last.isSuccess) return last
            if (!hasBlePermissions() || !isBluetoothEnabled()) return last
            delay(450L + attempt * 350L)
        }
        return last
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectOnce(device: LedDevice): Result<BleChipset> = withContext(Dispatchers.IO) {
        if (!hasBlePermissions()) {
            return@withContext Result.failure(IllegalStateException("Нет разрешений Bluetooth"))
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Включите Bluetooth на телефоне"))
        }

        stopScan()
        disconnect()

        val remote = runCatching { adapter.getRemoteDevice(device.address) }.getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Неверный MAC-адрес устройства"))

        intentionalDisconnect = false
        isMelkFirmware = device.name.trim().uppercase().let { it.startsWith("MELK") || it.contains("BLEDOB") }
        val connectDeferred = CompletableDeferred<Result<BleChipset>>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                // Ignore late callbacks from an older, already replaced GATT session
                if (activeGatt != null && activeGatt != gatt) {
                    runCatching { gatt.close() }
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    // Only one GATT operation may be in flight at a time on Android, so service
                    // discovery is the single request here (9-byte frames don't need a bigger MTU).
                    // A short pause before discovery avoids status 129/133 on many phones.
                    Thread.sleep(300L)
                    if (!gatt.discoverServices() && !connectDeferred.isCompleted) {
                        connectDeferred.complete(
                            Result.failure(IllegalStateException("Не удалось прочитать сервисы ленты"))
                        )
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    writeCharacteristic = null
                    pendingWriteDeferred?.complete(false)
                    pendingWriteDeferred = null

                    if (!connectDeferred.isCompleted) {
                        val reason = if (status == 133 || status == 62 || status == 8) {
                            "Лента не ответила по Bluetooth. Подойдите ближе и закройте другие приложения ленты (Lotus Lantern)."
                        } else {
                            "Лента разорвала BLE соединение (код $status)"
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

                val allChars = gatt.services.flatMap { it.characteristics }
                val lotusChar = allChars.firstOrNull { it.uuid in lotusWriteUuids }
                val trionesChar = allChars.firstOrNull { it.uuid in trionesWriteUuids }
                val fallbackChar = allChars.firstOrNull { char ->
                    val p = char.properties
                    (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                        (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                }

                val chosenChar = lotusChar ?: trionesChar ?: fallbackChar
                val chipset = when {
                    lotusChar != null -> BleChipset.LOTUS_ELK
                    trionesChar != null -> BleChipset.TRIONES_HAPPY
                    else -> BleChipset.LOTUS_ELK
                }

                if (chosenChar != null) {
                    writeCharacteristic = chosenChar
                    detectedChipset = chipset
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(Result.success(chipset))
                    }
                } else if (!connectDeferred.isCompleted) {
                    connectDeferred.complete(
                        Result.failure(IllegalStateException("Не найден канал управления LED (0xFFF3 / 0xFFD9)"))
                    )
                }
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingWriteDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        // Always enforce TRANSPORT_LE to prevent Android GATT error 133 on dual-mode adapters
        val gatt = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        activeGatt = gatt
        if (gatt == null) {
            return@withContext Result.failure(IllegalStateException("Android отказал в BLE подключении"))
        }

        val result = withTimeoutOrNull(10_000L) { connectDeferred.await() }
            ?: Result.failure(IllegalStateException("Таймаут подключения по Bluetooth. Проверьте питание ленты."))

        if (result.isFailure) {
            disconnect()
        } else if (detectedChipset == BleChipset.LOTUS_ELK) {
            // Wake MELK-family firmware and push the hardware dimmer to 100 %:
            // brightness is applied in software to every colour frame.
            if (isMelkFirmware) {
                LedProtocolPackets.lotusMelkInit().forEach { writeBytesSerialized(it) }
            }
            writeBytesSerialized(LedProtocolPackets.lotusBrightness(100))
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

    suspend fun sendPower(on: Boolean): Boolean {
        return when (detectedChipset) {
            BleChipset.LOTUS_ELK -> {
                val ok = writeBytesSerialized(LedProtocolPackets.lotusPower(on))
                if (isMelkFirmware) writeBytesSerialized(LedProtocolPackets.lotusPowerMelk(on)) else ok
            }
            BleChipset.TRIONES_HAPPY -> writeBytesSerialized(LedProtocolPackets.trionesPower(on))
        }
    }

    suspend fun sendColor(color: RgbColor, pinOrder: PinOrder): Boolean {
        val packet = when (detectedChipset) {
            BleChipset.LOTUS_ELK -> LedProtocolPackets.lotusColor(color, pinOrder)
            BleChipset.TRIONES_HAPPY -> LedProtocolPackets.trionesColor(color, pinOrder)
        }
        return writeBytesSerialized(packet)
    }

    suspend fun sendBuiltInMode(modeCode: Int, speedPercent: Int): Boolean {
        return when (detectedChipset) {
            BleChipset.LOTUS_ELK -> {
                val ok1 = writeBytesSerialized(LedProtocolPackets.lotusBuiltInMode(modeCode))
                val ok2 = writeBytesSerialized(LedProtocolPackets.lotusSpeed(speedPercent))
                ok1 && ok2
            }
            BleChipset.TRIONES_HAPPY -> {
                writeBytesSerialized(LedProtocolPackets.trionesBuiltInMode(modeCode, speedPercent))
            }
        }
    }

    /**
     * Channel order is remapped in software inside every colour frame; a hardware re-map on top
     * of that would swap the channels twice, so nothing is sent here.
     */
    @Suppress("UNUSED_PARAMETER")
    fun sendPinOrder(pinOrder: PinOrder): Boolean = true

    fun isConnected(): Boolean = activeGatt != null && writeCharacteristic != null

    suspend fun sendExternalMic(enabled: Boolean, sensitivityPercent: Int): Boolean {
        return when (detectedChipset) {
            BleChipset.LOTUS_ELK ->
                writeBytesSerialized(LedProtocolPackets.lotusExternalMic(enabled, sensitivityPercent))
            BleChipset.TRIONES_HAPPY -> false
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun writeBytesSerialized(payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val gatt = activeGatt ?: return@withLock false
            val char = writeCharacteristic ?: return@withLock false
            if (!hasBlePermissions()) return@withLock false

            val ack = CompletableDeferred<Boolean>()
            pendingWriteDeferred = ack

            val started = runCatching {
                val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
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

            val completed = withTimeoutOrNull(140L) { ack.await() } ?: true
            pendingWriteDeferred = null
            completed
        }
    }
}
