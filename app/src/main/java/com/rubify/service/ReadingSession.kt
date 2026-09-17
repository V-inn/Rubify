package com.rubify.service

import com.rubify.overlay.OverlayContent

/**
 * When to read the screen and what to show. Pure logic, driven by the
 * service on the main thread. Every reading starts from an explicit tap.
 *
 * A button tap starts a session: the screen is read, then pinyin is shown
 * together with a small control bubble. The bubble's Refresh reads the
 * screen again (after scrolling, say), its Hide/Show toggles the last
 * pinyin without reading, and its Close, or another button tap, ends the
 * session. Each reading carries the generation it started in; results from
 * before a refresh, a rotation or the end of the session are dropped.
 */
class ReadingSession(private val host: Host) {

    interface Host {
        /** Captures and reads the screen, then reports back with [generation]. */
        fun startReading(generation: Int, settleDelayMs: Long)
        fun showPinyin(content: OverlayContent)
        fun hidePinyin()
        /** Shows the bubble, or updates it, with the pinyin toggle's state. */
        fun showControls(pinyinVisible: Boolean)
        fun hideControls()
        /** Runs [action] after [delayMs], replacing any pending action. */
        fun schedule(delayMs: Long, action: () -> Unit)
        fun cancelScheduled()
    }

    var isActive = false
        private set

    private var generation = 0
    private var pendingReadings = 0
    private var shownInSession = false
    private var retries = 0

    /** The pinyin of the current screen, kept while hidden; null once stale. */
    private var lastContent: OverlayContent? = null
    private var pinyinVisible = false

    fun onButtonClicked() {
        if (isActive) end() else start()
    }

    fun onCloseRequested() {
        if (isActive) end()
    }

    /** Hides the pinyin, or shows the last one again (reading only if stale). */
    fun onTogglePinyinRequested() {
        if (!isActive || pendingReadings > 0) return
        val content = lastContent
        when {
            pinyinVisible -> {
                host.hidePinyin()
                pinyinVisible = false
                host.showControls(pinyinVisible = false)
            }
            content != null -> {
                host.showPinyin(content)
                pinyinVisible = true
                host.showControls(pinyinVisible = true)
            }
            else -> onRefreshRequested()
        }
    }

    fun onRefreshRequested() {
        if (!isActive || pendingReadings > 0) return
        generation++
        // Neither our pinyin nor the bubble may end up in the screenshot.
        host.hidePinyin()
        host.hideControls()
        lastContent = null
        pinyinVisible = false
        retries = 0
        read(REFRESH_SETTLE_MS)
    }

    /** The display rotated: the pinyin no longer lines up. */
    fun onScreenMoved() {
        if (!isActive) return
        generation++
        lastContent = null
        host.hidePinyin()
        if (pinyinVisible) {
            pinyinVisible = false
            if (pendingReadings == 0) host.showControls(pinyinVisible = false)
        }
    }

    fun onReadingFinished(generation: Int, content: OverlayContent?) {
        pendingReadings--
        if (!isActive) return
        if (generation != this.generation) {
            if (pendingReadings == 0 && shownInSession) host.showControls(pinyinVisible)
            return
        }
        retries = 0
        if (content != null && content.labelCount > 0) {
            lastContent = content
            pinyinVisible = true
            shownInSession = true
            host.showPinyin(content)
            host.showControls(pinyinVisible = true)
        } else if (shownInSession) {
            // Nothing to annotate here; keep the bubble for another refresh.
            lastContent = null
            pinyinVisible = false
            host.showControls(pinyinVisible = false)
        } else {
            // Nothing on the first read: don't leave an invisible session
            // running for the next tap to end.
            end()
        }
    }

    fun onReadingFailed(generation: Int) {
        pendingReadings--
        if (!isActive) return
        if (generation != this.generation) {
            if (pendingReadings == 0 && shownInSession) host.showControls(pinyinVisible)
            return
        }
        if (retries < MAX_RETRIES) {
            // Usually the system's one-screenshot-per-second limit.
            retries++
            host.schedule(RETRY_DELAY_MS) { retry(generation) }
        } else if (shownInSession) {
            host.showControls(pinyinVisible)
        } else {
            end()
        }
    }

    private fun start() {
        isActive = true
        shownInSession = false
        lastContent = null
        pinyinVisible = false
        retries = 0
        generation++
        read(BUTTON_SETTLE_MS)
    }

    private fun end() {
        isActive = false
        generation++
        lastContent = null
        pinyinVisible = false
        host.cancelScheduled()
        host.hidePinyin()
        host.hideControls()
    }

    private fun retry(generation: Int) {
        if (isActive && generation == this.generation) read(0)
    }

    private fun read(settleDelayMs: Long) {
        pendingReadings++
        host.startReading(generation, settleDelayMs)
    }

    companion object {
        /**
         * Wait after a button tap: when another service shares the button,
         * the tap opens a chooser, and capturing too early (~50 ms after the
         * click, measured) records it and its dim scrim.
         */
        const val BUTTON_SETTLE_MS = 400L

        /** Wait for our own removed windows to leave the screen. */
        const val REFRESH_SETTLE_MS = 150L

        const val RETRY_DELAY_MS = 1000L
        const val MAX_RETRIES = 2
    }
}
