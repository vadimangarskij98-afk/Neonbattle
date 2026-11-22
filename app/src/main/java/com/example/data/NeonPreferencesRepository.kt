package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ControllerProtocol
import com.example.model.FlowDirection
import com.example.model.LedDevice
import com.example.model.LightScene
import com.example.model.PinOrder
import com.example.model.RgbColor
import com.example.model.SceneCategory
import com.example.model.ScheduleAction
import com.example.model.ScheduleRule
import com.example.model.TransitionCurve
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.neonDataStore: DataStore<Preferences> by preferencesDataStore(name = "neonflow_prefs")

data class StoredAppSettings(
    val pinOrder: PinOrder = PinOrder.RGB,
    val softPowerFade: Boolean = true,
    val fpsLimit: Int = 25,
    val brightness: Float = 0.88f,
    val smoothness: Float = 0.80f,
    val speed: Float = 0.48f,
    val zoneMeters: Float = 1.0f,
    val flowDirection: FlowDirection = FlowDirection.FORWARD,
    val favoriteColors: List<RgbColor> = defaultFavoriteColors,
    val savedDevices: List<LedDevice> = emptyList(),
    val lastDeviceId: String? = null,
    val customScenes: List<LightScene> = emptyList(),
    val schedules: List<ScheduleRule> = emptyList()
) {
    companion object {
        val defaultFavoriteColors = listOf(
            RgbColor(255, 18, 48),
            RgbColor(0, 229, 255),
            RgbColor(255, 46, 147),
            RgbColor(139, 92, 246),
            RgbColor(255, 128, 0),
            RgbColor(34, 229, 139)
        )
    }
}

class NeonPreferencesRepository(private val context: Context) {

    private object Keys {
        val PIN_ORDER = stringPreferencesKey("pin_order")
        val SOFT_POWER_FADE = booleanPreferencesKey("soft_power_fade")
        val FPS_LIMIT = intPreferencesKey("fps_limit")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val SMOOTHNESS = floatPreferencesKey("smoothness")
        val SPEED = floatPreferencesKey("speed")
        val ZONE_METERS = floatPreferencesKey("zone_meters")
        val FLOW_DIRECTION = stringPreferencesKey("flow_direction")
        val FAVORITES_JSON = stringPreferencesKey("favorites_json")
        val DEVICES_JSON = stringPreferencesKey("devices_json")
        val LAST_DEVICE_ID = stringPreferencesKey("last_device_id")
        val CUSTOM_SCENES_JSON = stringPreferencesKey("custom_scenes_json")
        val SCHEDULES_JSON = stringPreferencesKey("schedules_json")
    }

    val settingsFlow: Flow<StoredAppSettings> = context.neonDataStore.data.map { prefs ->
        StoredAppSettings(
            pinOrder = prefs[Keys.PIN_ORDER]?.let { name ->
                PinOrder.entries.firstOrNull { it.name == name }
            } ?: PinOrder.RGB,
            softPowerFade = prefs[Keys.SOFT_POWER_FADE] ?: true,
            fpsLimit = (prefs[Keys.FPS_LIMIT] ?: 25).coerceIn(10, 30),
            brightness = (prefs[Keys.BRIGHTNESS] ?: 0.88f).coerceIn(0.05f, 1f),
            smoothness = (prefs[Keys.SMOOTHNESS] ?: 0.80f).coerceIn(0f, 1f),
            speed = (prefs[Keys.SPEED] ?: 0.48f).coerceIn(0.05f, 1f),
            zoneMeters = (prefs[Keys.ZONE_METERS] ?: 1.0f).coerceIn(0.35f, 2.5f),
            flowDirection = prefs[Keys.FLOW_DIRECTION]?.let { dir ->
                FlowDirection.entries.firstOrNull { it.name == dir }
            } ?: FlowDirection.FORWARD,
            favoriteColors = prefs[Keys.FAVORITES_JSON]?.let(::decodeColors)
                ?: StoredAppSettings.defaultFavoriteColors,
            savedDevices = prefs[Keys.DEVICES_JSON]?.let(::decodeDevices) ?: emptyList(),
            lastDeviceId = prefs[Keys.LAST_DEVICE_ID],
            customScenes = prefs[Keys.CUSTOM_SCENES_JSON]?.let(::decodeScenes) ?: emptyList(),
            schedules = prefs[Keys.SCHEDULES_JSON]?.let(::decodeSchedules) ?: emptyList()
        )
    }

    suspend fun savePinOrder(pinOrder: PinOrder) {
        context.neonDataStore.edit { it[Keys.PIN_ORDER] = pinOrder.name }
    }

    suspend fun saveSoftPowerFade(enabled: Boolean) {
        context.neonDataStore.edit { it[Keys.SOFT_POWER_FADE] = enabled }
    }

    suspend fun saveFpsLimit(fps: Int) {
        context.neonDataStore.edit { it[Keys.FPS_LIMIT] = fps.coerceIn(10, 30) }
    }

    suspend fun saveSliders(
        brightness: Float,
        smoothness: Float,
        speed: Float,
        zoneMeters: Float,
        flowDirection: FlowDirection
    ) {
        context.neonDataStore.edit {
            it[Keys.BRIGHTNESS] = brightness
            it[Keys.SMOOTHNESS] = smoothness
            it[Keys.SPEED] = speed
            it[Keys.ZONE_METERS] = zoneMeters
            it[Keys.FLOW_DIRECTION] = flowDirection.name
        }
    }

    suspend fun saveFavorites(colors: List<RgbColor>) {
        context.neonDataStore.edit { it[Keys.FAVORITES_JSON] = encodeColors(colors) }
    }

    suspend fun saveDevices(devices: List<LedDevice>, lastConnectedId: String?) {
        context.neonDataStore.edit {
            it[Keys.DEVICES_JSON] = encodeDevices(devices)
            if (lastConnectedId != null) {
                it[Keys.LAST_DEVICE_ID] = lastConnectedId
            }
        }
    }

    suspend fun saveCustomScenes(scenes: List<LightScene>) {
        context.neonDataStore.edit { it[Keys.CUSTOM_SCENES_JSON] = encodeScenes(scenes) }
    }

    suspend fun saveSchedules(schedules: List<ScheduleRule>) {
        context.neonDataStore.edit { it[Keys.SCHEDULES_JSON] = encodeSchedules(schedules) }
    }

    private fun encodeColors(colors: List<RgbColor>): String {
        val arr = JSONArray()
        colors.forEach { arr.put(it.toHexString()) }
        return arr.toString()
    }

    private fun decodeColors(raw: String): List<RgbColor> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { RgbColor.fromHex(arr.getString(it)) }
            .ifEmpty { StoredAppSettings.defaultFavoriteColors }
    }.getOrDefault(StoredAppSettings.defaultFavoriteColors)

    private fun encodeDevices(devices: List<LedDevice>): String {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("address", d.address)
                put("port", d.port)
                put("protocol", d.protocol.name)
                put("mac", d.macAddress)
            })
        }
        return arr.toString()
    }

    private fun decodeDevices(raw: String): List<LedDevice> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            val proto = ControllerProtocol.entries.firstOrNull {
                it.name == obj.optString("protocol")
            } ?: ControllerProtocol.WIFI_MAGIC_HOME
            LedDevice(
                id = obj.getString("id"),
                name = obj.getString("name"),
                address = obj.getString("address"),
                port = obj.optInt("port", proto.defaultPort),
                protocol = proto,
                macAddress = obj.optString("mac", ""),
                isSaved = true
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeScenes(scenes: List<LightScene>): String {
        val arr = JSONArray()
        scenes.forEach { s ->
            val colorsArr = JSONArray()
            s.colors.forEach { colorsArr.put(it.toHexString()) }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("subtitle", s.subtitle)
                put("colors", colorsArr)
                put("curve", s.curve.name)
                put("speed", s.defaultSpeed.toDouble())
                put("smoothness", s.defaultSmoothness.toDouble())
                put("zoneMeters", s.defaultZoneMeters.toDouble())
            })
        }
        return arr.toString()
    }

    private fun decodeScenes(raw: String): List<LightScene> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            val cArr = obj.getJSONArray("colors")
            val colors = List(cArr.length()) { idx -> RgbColor.fromHex(cArr.getString(idx)) }
            val curve = TransitionCurve.entries.firstOrNull { it.name == obj.optString("curve") }
                ?: TransitionCurve.RUNNING_ZONES
            LightScene(
                id = obj.getString("id"),
                name = obj.getString("name"),
                subtitle = obj.optString("subtitle", "Авторская сцена"),
                category = SceneCategory.CUSTOM,
                colors = colors.ifEmpty { listOf(RgbColor.Crimson, RgbColor.Cyan) },
                curve = curve,
                defaultSpeed = obj.optDouble("speed", 0.48).toFloat(),
                defaultSmoothness = obj.optDouble("smoothness", 0.82).toFloat(),
                defaultZoneMeters = obj.optDouble("zoneMeters", 1.0).toFloat(),
                isCustom = true
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeSchedules(rules: List<ScheduleRule>): String {
        val arr = JSONArray()
        rules.forEach { r ->
            val daysArr = JSONArray()
            r.daysOfWeek.sorted().forEach { daysArr.put(it) }
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("label", r.label)
                put("hour", r.hour)
                put("minute", r.minute)
                put("days", daysArr)
                put("action", r.action.name)
                put("colorHex", r.colorHex)
                put("brightness", r.brightness.toDouble())
                put("enabled", r.enabled)
            })
        }
        return arr.toString()
    }

    private fun decodeSchedules(raw: String): List<ScheduleRule> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            val daysArr = obj.getJSONArray("days")
            val days = buildSet {
                for (j in 0 until daysArr.length()) add(daysArr.getInt(j))
            }
            val action = ScheduleAction.entries.firstOrNull { it.name == obj.optString("action") }
                ?: ScheduleAction.TURN_ON
            ScheduleRule(
                id = obj.getString("id"),
                label = obj.optString("label", "Расписание"),
                hour = obj.optInt("hour", 22),
                minute = obj.optInt("minute", 0),
                daysOfWeek = days.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) },
                action = action,
                colorHex = obj.optString("colorHex", "#FF1230"),
                brightness = obj.optDouble("brightness", 0.85).toFloat(),
                enabled = obj.optBoolean("enabled", true)
            )
        }
    }.getOrDefault(emptyList())
}
