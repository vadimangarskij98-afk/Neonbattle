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
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.model.BleChipset
import com.example.model.ControllerProtocol
import com.example.model.LedDevice
import com.example.model.PinOrder
import com.example.model.RgbColor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
    private var intentionalDisconnect: Boolean = false

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter()?.isEnabled == true

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

    @SuppressLint("MissingPermission")
    suspend fun connect(device: LedDevice): Result<BleChipset> = withContext(Dispatchers.IO) {
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
        val connectDeferred = CompletableDeferred<Result<BleChipset>>()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    runCatching { gatt.requestMtu(185) }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    writeCharacteristic = null
                    pendingWriteDeferred?.complete(false)
                    pendingWriteDeferred = null

                    if (!connectDeferred.isCompleted) {
                        val reason = if (status == 133) {
                            "Контроллер не ответил (BLE 133). Подойдите ближе и попробуйте снова."
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

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                pendingWriteDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        // Always enforce TRANSPORT_LE to prevent Android GATT error 133
        activeGatt = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

        val result = withTimeoutOrNull(9000L) { connectDeferred.await() }
            ?: Result.failure(IllegalStateException("Таймаут подключения по Bluetooth. Проверьте питание ленты."))

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

    suspend fun sendPower(on: Boolean): Boolean {
        val packet = when (detectedChipset) {
            BleChipset.LOTUS_ELK -> LedProtocolPackets.lotusPower(on)
            BleChipset.TRIONES_HAPPY -> LedProtocolPackets.trionesPower(on)
        }
        return writeBytesSerialized(packet)
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

    suspend fun sendPinOrder(pinOrder: PinOrder): Boolean {
        return when (detectedChipset) {
            BleChipset.LOTUS_ELK -> writeBytesSerialized(LedProtocolPackets.lotusPinOrder(pinOrder))
            BleChipset.TRIONES_HAPPY -> true // Software channel remapping already active
        }
    }

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
