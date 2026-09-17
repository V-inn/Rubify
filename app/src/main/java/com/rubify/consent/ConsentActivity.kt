package com.rubify.consent

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.rubify.R
import com.rubify.service.RubifyAccessibilityService

/**
 * The prominent disclosure: what the accessibility service does, what it
 * accesses and what it never does, with an explicit choice. Shown on first
 * launch, when the service is used without consent, and from the main
 * screen, where consent can also be withdrawn.
 */
class ConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consent)
        bind()
    }

    private fun bind() {
        val given = Consent.isGiven(this)
        val primary = findViewById<Button>(R.id.consent_primary)
        val secondary = findViewById<Button>(R.id.consent_secondary)
        findViewById<TextView>(R.id.consent_given_note).visibility =
            if (given) View.VISIBLE else View.GONE

        if (given) {
            primary.setText(R.string.consent_close)
            primary.setOnClickListener { finish() }
            secondary.setText(R.string.consent_withdraw)
            secondary.setOnClickListener {
                Consent.withdraw(this)
                RubifyAccessibilityService.running()?.onConsentWithdrawn()
                bind()
            }
        } else {
            primary.setText(R.string.consent_agree)
            primary.setOnClickListener {
                Consent.give(this)
                setResult(RESULT_OK)
                finish()
            }
            secondary.setText(R.string.consent_decline)
            secondary.setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
}
