package io.github.v_inn.rubify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Opens the published privacy policy (site/privacy/) in the user's browser. */
object PrivacyPolicy {
    fun open(context: Context) {
        val uri = Uri.parse(context.getString(R.string.privacy_policy_url))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "No app can open $uri", e)
        }
    }
}
