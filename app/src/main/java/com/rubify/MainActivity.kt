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
import com.rubify.consent.Consent
import com.rubify.consent.ConsentActivity
import com.rubify.service.RubifyAccessibilityService
import com.rubify.tile.ReadingTileService

/**
 * Entry point and the service's settings screen: service state, the way to
 * turn it on (only after the disclosure is accepted), the tile, privacy and
 * licenses. The disclosure opens by itself on first launch.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var primaryAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.service_status)
        primaryAction = findViewById(R.id.primary_action)

        val addTile = findViewById<Button>(R.id.add_tile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTile.setOnClickListener { requestAddTile() }
        } else {
            addTile.visibility = View.GONE
        }
        findViewById<Button>(R.id.privacy_and_consent).setOnClickListener { openDisclosure() }
        findViewById<Button>(R.id.open_source_licenses).setOnClickListener {
            startActivity(Intent(this, LicensesActivity::class.java))
        }

        if (savedInstanceState == null && !Consent.isGiven(this)) openDisclosure()
    }

    override fun onResume() {
        super.onResume()
        val consent = Consent.isGiven(this)
        statusView.setText(
            when {
                !consent -> R.string.status_consent_needed
                isServiceEnabled() -> R.string.status_enabled
                else -> R.string.status_disabled
            },
        )
        if (consent) {
            primaryAction.setText(R.string.open_accessibility_settings)
            primaryAction.setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        } else {
            primaryAction.setText(R.string.review_and_agree)
            primaryAction.setOnClickListener { openDisclosure() }
        }
    }

    private fun openDisclosure() {
        startActivity(Intent(this, ConsentActivity::class.java))
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
