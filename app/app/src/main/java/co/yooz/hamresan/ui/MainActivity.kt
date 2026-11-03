package co.yooz.hamresan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.yooz.hamresan.App
import co.yooz.hamresan.mesh.MeshService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeId by App.instance.prefs.themeFlow.collectAsStateWithLifecycle()
            HamresanTheme(Skin.of(themeId)) {
                Root(
                    hasPermissions = { hasMeshPermissions() },
                    neededPermissions = { meshPermissions() },
                    startMesh = { startMeshIfAllowed() },
                    stopMesh = { MeshService.stop(this) },
                    openBatterySettings = { openBatterySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startMeshIfAllowed()
    }

    private fun startMeshIfAllowed() {
        val app = App.instance
        if (app.prefs.onboarded && app.prefs.meshEnabled && hasMeshPermissions() && !app.mesh.running.value) {
            MeshService.start(this)
        }
    }

    fun meshPermissions(): Array<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
            list += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return list.toTypedArray()
    }

    fun hasMeshPermissions(): Boolean = meshPermissions().all {
        // notifications are nice-to-have, not required for Nearby itself
        it == Manifest.permission.POST_NOTIFICATIONS ||
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun openBatterySettings() {
        try {
            if (Build.VERSION.SDK_INT >= 23) startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {}
    }
}
