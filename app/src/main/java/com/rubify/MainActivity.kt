package com.rubify

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.rubify.service.RubifyAccessibilityService
import com.rubify.tile.ReadingTileService

/**
 * Entry point and the service's settings screen. Shows whether the
 * accessibility service is enabled, links to the system settings, and on
 * Android 13+ offers to add the Quick Settings tile.
 * The first-run consent screen replaces this in phase 8.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.service_status)
        findViewById<Button>(R.id.open_accessibility_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val addTile = findViewById<Button>(R.id.add_tile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTile.setOnClickListener { requestAddTile() }
        } else {
            addTile.visibility = View.GONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, ReadingTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result -> Log.i(LOG_TAG, "Add tile request result: $result") }
    }

    override fun onResume() {
        super.onResume()
        statusView.setText(
            if (isServiceEnabled()) R.string.status_enabled else R.string.status_disabled
        )
    }

    private fun isServiceEnabled(): Boolean {
        val ours = ComponentName(this, RubifyAccessibilityService::class.java)
        return getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                service.packageName == ours.packageName && service.name == ours.className
            }
    }
}
