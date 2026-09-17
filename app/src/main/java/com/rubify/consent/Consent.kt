package com.rubify.consent

import android.annotation.SuppressLint
import android.content.Context

/**
 * The user's agreement to the prominent disclosure (ConsentActivity), which
 * Play requires before the accessibility service may read the screen.
 * Without it the service does nothing but point the user to the disclosure.
 */
object Consent {

    /** Bump when the disclosure changes materially: everyone is asked again. */
    const val DISCLOSURE_VERSION = 1

    private const val PREFS = "consent"
    private const val KEY_ACCEPTED_VERSION = "accepted_disclosure_version"

    fun isGiven(context: Context): Boolean =
        prefs(context).getInt(KEY_ACCEPTED_VERSION, 0) >= DISCLOSURE_VERSION

    // The edit {} helper lives in core-ktx, which the app doesn't depend on.
    @SuppressLint("UseKtx")
    fun give(context: Context) {
        prefs(context).edit().putInt(KEY_ACCEPTED_VERSION, DISCLOSURE_VERSION).apply()
    }

    @SuppressLint("UseKtx")
    fun withdraw(context: Context) {
        prefs(context).edit().remove(KEY_ACCEPTED_VERSION).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
