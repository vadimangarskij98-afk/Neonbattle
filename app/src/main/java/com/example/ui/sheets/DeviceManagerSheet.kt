package com.example.ui.sheets

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BleChipset
import com.example.model.ConnectionState
import com.example.model.ControllerProtocol
import com.example.model.LedDevice
import com.example.model.RgbColor
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusGreen
import com.example.viewmodel.NeonUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagerSheet(
    state: NeonUiState,
    hasBlePermissions: () -> Boolean,
    onRefreshConnectivity: () -> Unit,
    onDismiss: () -> Unit,
    onScanWifi: () -> Unit,
    onScanBle: () -> Unit,
    onStopBleScan: () -> Unit,
    onAddManualWifi: (String, String, Int, ControllerProtocol) -> Unit,
    onConnectDevice: (LedDevice) -> Unit,
    onRemoveSavedDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSelectBleChipset: (BleChipset) -> Unit,
    onTestColor: (RgbColor) -> Unit,
    onTogglePower: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Open directly on Bluetooth LE when connected via BLE or when Wi-Fi is unavailable
    val connectedBle = (state.connectionState as? ConnectionState.Connected)?.takeIf { !it.device.protocol.isWifi }
    var transportTab by remember {
        mutableIntStateOf(if (connectedBle != null || !state.isWifiAvailable) 1 else 0)
    }
    var showManualForm by remember { mutableStateOf(false) }
    var showAllBleDevices by remember { mutableStateOf(false) }

    var manualName by remember { mutableStateOf("Неоновая лента") }
    var manualIp by remember { mutableStateOf("192.168.1.100") }
    var manualPort by remember { mutableStateOf("5577") }
    var manualProtocol by remember { mutableStateOf(ControllerProtocol.WIFI_MAGIC_HOME) }

    LaunchedEffect(Unit) {
        onRefreshConnectivity()
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onScanBle()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.devices_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.connectionState is ConnectionState.Connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.testTag("disconnect_device_button")
                    ) {
                        Text(stringResource(R.string.btn_disconnect))
                    }
                }
            }

            // Show explicit connection error card inside sheet so the user always sees what happened
            val connErr = state.connectionState as? ConnectionState.Error
            if (connErr != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StatusError.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusError.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = StatusError
                        )
                        Text(
                            text = connErr.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Offline Wi-Fi -> Direct BLE guidance banner
            if (!state.isWifiAvailable) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StatusAmber.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = StatusAmber
                        )
                        Text(
                            text = "Wi-Fi сейчас недоступен — используйте прямое подключение по Bluetooth LE ниже.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    stringResource(R.string.tab_wifi),
                    "Bluetooth LE"
                ).forEachIndexed { idx, title ->
                    SegmentedButton(
                        selected = transportTab == idx,
                        onClick = { transportTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 2),
                        modifier = Modifier.testTag("device_transport_tab_$idx")
                    ) {
                        Text(title)
                    }
                }
            }

            if (transportTab == 0) {
                // Wi-Fi Discovery + Manual IP controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onScanWifi,
                        enabled = !state.isWifiScanning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_wifi_button")
                    ) {
                        if (state.isWifiScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Поиск…")
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_scan_wifi))
                        }
                    }

                    FilledTonalButton(
                        onClick = { showManualForm = !showManualForm },
                        modifier = Modifier.testTag("toggle_manual_ip_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_add_manual))
                    }
                }

                if (showManualForm) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Подключение по локальному IP", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                value = manualName,
                                onValueChange = { manualName = it },
                                label = { Text("Название ленты") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualIp,
                                    onValueChange = { manualIp = it },
                                    label = { Text("IP адрес") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier
                                        .weight(2f)
                                        .testTag("manual_ip_input")
                                )
                                OutlinedTextField(
                                    value = manualPort,
                                    onValueChange = { manualPort = it.filter(Char::isDigit) },
                                    label = { Text("Порт") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("manual_port_input")
                                )
                            }

                            Text("Протокол контроллера", style = MaterialTheme.typography.labelLarge)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ControllerProtocol.entries.filter { it.isWifi }.forEach { proto ->
                                    FilterChip(
                                        selected = manualProtocol == proto,
                                        onClick = {
                                            manualProtocol = proto
                                            manualPort = proto.defaultPort.toString()
                                        },
                                        label = {
                                            Column {
                                                Text(proto.displayName, style = MaterialTheme.typography.labelLarge)
                                                Text(proto.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onAddManualWifi(
                                        manualName,
                                        manualIp,
                                        manualPort.toIntOrNull() ?: manualProtocol.defaultPort,
                                        manualProtocol
                                    )
                                    showManualForm = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_manual_wifi_button")
                            ) {
                                Text("Сохранить и подключиться")
                            }
                        }
                    }
                }

                val wifiSaved = state.savedDevices.filter { it.protocol.isWifi }
                if (wifiSaved.isNotEmpty()) {
                    Text("Сохранённые Wi-Fi ленты", style = MaterialTheme.typography.titleSmall)
                    wifiSaved.forEach { dev ->
                        DeviceRowCard(
                            device = dev,
                            connectionState = state.connectionState,
                            onConnect = { onConnectDevice(dev) },
                            onDelete = { onRemoveSavedDevice(dev.id) }
                        )
                    }
                }

                if (state.discoveredWifiDevices.isNotEmpty()) {
                    Text("Найденные в сети Wi-Fi", style = MaterialTheme.typography.titleSmall)
                    state.discoveredWifiDevices.forEach { dev ->
                        DeviceRowCard(
                            device = dev,
                            connectionState = state.connectionState,
                            onConnect = { onConnectDevice(dev) },
                            onDelete = null
                        )
                    }
                } else if (wifiSaved.isEmpty() && !showManualForm) {
                    Text(
                        text = "Подключите телефон к Wi-Fi сети ленты и нажмите «Найти в Wi-Fi», либо переключитесь на «Bluetooth LE» для управления напрямую без Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Critical real-world Lotus Lantern hint
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Важно: полностью закройте оригинальное приложение Lotus Lantern из недавних, иначе оно удерживает Bluetooth ленты и она не видна в поиске.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Live BLE Protocol Selector & Connection Test Card when connected via BLE
                if (connectedBle != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, StatusGreen.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Проверка связи и протокол BLE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (connectedBle.gattChannelInfo.isNotBlank()) {
                                    Text(
                                        text = connectedBle.gattChannelInfo,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = StatusGreen
                                    )
                                }
                            }
                            Text(
                                text = "Нажмите кнопку цвета ниже. Если лента не реагирует — переключите протокол чипа, тест отправится автоматически:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 5 Live BLE Protocol Dialect Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BleChipset.entries.forEach { chipset ->
                                    FilterChip(
                                        selected = state.bleChipset == chipset,
                                        onClick = { onSelectBleChipset(chipset) },
                                        label = { Text(chipset.shortName) },
                                        modifier = Modifier.testTag("ble_chipset_${chipset.name.lowercase()}")
                                    )
                                }
                            }

                            // Instant R / G / B / Power Verification Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onTestColor(RgbColor(255, 0, 0)) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE53935),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ble_test_red")
                                ) {
                                    Text("Красн.")
                                }
                                Button(
                                    onClick = { onTestColor(RgbColor(0, 255, 0)) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00C853),
                                        contentColor = Color(0xFF061B0D)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ble_test_green")
                                ) {
                                    Text("Зел.")
                                }
                                Button(
                                    onClick = { onTestColor(RgbColor(0, 0, 255)) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2962FF),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ble_test_blue")
                                ) {
                                    Text("Синий")
                                }
                                FilledTonalButton(
                                    onClick = onTogglePower,
                                    modifier = Modifier.testTag("ble_test_power")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PowerSettingsNew,
                                        contentDescription = "Вкл/Выкл",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Direct Bluetooth LE Scan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (state.isBleScanning) {
                                onStopBleScan()
                            } else if (hasBlePermissions()) {
                                onScanBle()
                            } else {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                } else {
                                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                                blePermissionLauncher.launch(perms)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_ble_button")
                    ) {
                        if (state.isBleScanning) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Остановить поиск…")
                        } else {
                            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_scan_ble))
                        }
                    }
                }

                // Filter toggle: LED controllers vs all nearby BLE devices
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Показать все BLE-устройства рядом",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showAllBleDevices,
                        onCheckedChange = { showAllBleDevices = it },
                        modifier = Modifier.testTag("ble_show_all_switch")
                    )
                }

                val savedBle = state.savedDevices.filter { !it.protocol.isWifi }
                val scannedFiltered = state.discoveredBleDevices.filter {
                    showAllBleDevices || it.isLikelyLed
                }
                val combinedBle = (savedBle + scannedFiltered).distinctBy { it.address }

                if (combinedBle.isEmpty()) {
                    Text(
                        text = "Нажмите «Искать Bluetooth». Обычные имена лент Lotus Lantern: ELK-BLEDOM, MELK-OA, QHM, BJ_LED, LED_BLE, Triones. Если лента не нашлась — включите переключатель «Показать все BLE-устройства рядом».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    combinedBle.forEach { dev ->
                        DeviceRowCard(
                            device = dev,
                            connectionState = state.connectionState,
                            onConnect = { onConnectDevice(dev) },
                            onDelete = if (dev.isSaved) ({ onRemoveSavedDevice(dev.id) }) else null
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceRowCard(
    device: LedDevice,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val connectedObj = connectionState as? ConnectionState.Connected
    val isConnected = connectedObj?.device?.id == device.id
    val isConnecting = (connectionState as? ConnectionState.Connecting)?.device?.id == device.id

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isConnected) 1.5.dp else 1.dp,
                color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onConnect)
            .testTag("device_row_${device.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (device.protocol.isWifi) Icons.Rounded.Wifi else Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    tint = if (isConnected) StatusGreen else MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!device.protocol.isWifi && device.isLikelyLed) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (!device.protocol.isWifi && device.serviceHint.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = device.serviceHint,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = if (device.protocol.isWifi) {
                            "${device.address}:${device.port} • ${device.protocol.displayName}"
                        } else {
                            val chipLabel = if (isConnected && !connectedObj?.chipsetInfo.isNullOrBlank()) {
                                val ch = connectedObj!!.gattChannelInfo
                                if (ch.isNotBlank()) "${connectedObj.chipsetInfo} • $ch" else connectedObj.chipsetInfo
                            } else {
                                "Сигнал ${device.rssi} dBm"
                            }
                            "${device.address} • $chipLabel"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (isConnected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Подключено",
                        tint = StatusGreen
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Удалить устройство",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
