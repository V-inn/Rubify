package io.github.v_inn.rubify

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Open-source notices: the app's own list, then the pinyin data licenses. */
class LicensesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)
        // A few KB of text; reading it here is fine.
        val text = NOTICE_FILES.joinToString("\n\n") { path ->
            assets.open(path).bufferedReader().use { it.readText().trim() }
        }
        findViewById<TextView>(R.id.licenses_text).text = text
    }

    private companion object {
        val NOTICE_FILES = listOf("NOTICES.txt", "pinyin/LICENSES.txt")
    }
}
