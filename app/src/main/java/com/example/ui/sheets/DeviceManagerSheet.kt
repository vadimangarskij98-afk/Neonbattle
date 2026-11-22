package com.example.ui.sheets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.hardware.requiredBlePermissions
import com.example.model.ConnectionState
import com.example.model.ControllerProtocol
import com.example.model.LedDevice
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusAmber
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
    onDisconnect: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Automatically default to Bluetooth LE tab when Wi-Fi is unavailable!
    var transportTab by remember { mutableIntStateOf(if (state.isWifiAvailable) 0 else 1) }
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
        // Scan either way: if something was denied the ViewModel reports what is missing.
        if (results.isNotEmpty()) onScanBle()
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
                // Direct Bluetooth LE Control Tab
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
                                blePermissionLauncher.launch(requiredBlePermissions())
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
                        text = "Прямое управление без Wi-Fi для контроллеров Lotus Lantern, ELK-BLEDOM, MELK, Triones и HappyLighting. Включите Bluetooth рядом с лентой и нажмите «Искать Bluetooth».",
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
                    }
                    Text(
                        text = if (device.protocol.isWifi) {
                            "${device.address}:${device.port} • ${device.protocol.displayName}"
                        } else {
                            val chipLabel = if (isConnected && !connectedObj?.chipsetInfo.isNullOrBlank()) {
                                connectedObj!!.chipsetInfo
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
