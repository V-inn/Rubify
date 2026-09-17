package com.rubify.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rubify.MainActivity
import com.rubify.R
import com.rubify.service.RubifyAccessibilityService

/**
 * Quick Settings tile: the second trigger, for when the accessibility button
 * is missing (full-screen apps hide the navigation bar). A tap starts or
 * ends a session in the accessibility service, which runs in this process.
 * Without the service enabled, the tile opens Rubify instead.
 */
class ReadingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val service = RubifyAccessibilityService.running()
        if (service == null) {
            openApp()
            return
        }
        service.onTileClicked()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val service = RubifyAccessibilityService.running()
        val active = service?.isSessionActive == true
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(
            when {
                service == null -> R.string.tile_subtitle_set_up
                active -> R.string.tile_subtitle_on
                else -> R.string.tile_subtitle_off
            },
        )
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            openAppBeforeApi34(intent)
        }
    }

    // The Intent overload is the only one before API 34 (and throws from 34
    // on), so the deprecation doesn't apply where this runs.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openAppBeforeApi34(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
