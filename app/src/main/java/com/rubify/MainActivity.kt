package com.rubify

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import com.rubify.service.RubifyAccessibilityService

/**
 * Entry point and the service's settings screen. Shows whether the
 * accessibility service is enabled and links to the system settings.
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
