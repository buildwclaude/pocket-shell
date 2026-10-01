package com.pocketshell

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/**
 * Stage 1: one terminal screen running Android's built-in /system/bin/sh.
 *
 * The Termux libraries do the heavy lifting:
 *  - TerminalSession opens a pseudo-terminal (PTY) and starts the shell on it.
 *  - TerminalView draws the screen and turns touches/keys into bytes for the shell.
 * This activity is the glue: it starts the session and answers the callbacks both of
 * them make (keyboard, clipboard, font size, "the shell exited", ...).
 */
class MainActivity : Activity(), TerminalSessionClient {

    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null
    private var sessionStartedAt = 0L

    private var fontSizePx = 0
    private var minFontPx = 0
    private var maxFontPx = 0

    // One-shot modifiers from the extra-keys row: tap CTRL, then a letter -> Ctrl+letter.
    private var ctrlOn = false
    private var altOn = false
    private lateinit var ctrlKey: TextView
    private lateinit var altKey: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        minFontPx = dp(8)
        maxFontPx = dp(32)
        fontSizePx = dp(13)

        terminalView = findViewById(R.id.terminal)
        terminalView.setTerminalViewClient(viewClient)
        terminalView.setTypeface(Typeface.MONOSPACE)
        terminalView.setTextSize(fontSizePx)

        buildExtraKeys(findViewById(R.id.extra_keys))
        startSession()
    }

    override fun onResume() {
        super.onResume()
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        session?.finishIfRunning()
        super.onDestroy()
    }

    /** Starts /system/bin/sh in the app's private home folder. */
    private fun startSession() {
        val home = File(filesDir, "home").apply { mkdirs() }
        val tmp = File(cacheDir, "tmp").apply { mkdirs() }

        // Keep Android's own variables (ANDROID_ROOT, BOOTCLASSPATH, ...): the system
        // tools in /system/bin need them. Then point HOME/TMPDIR at our sandbox.
        val env = System.getenv().toMutableMap().apply {
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("HOME", home.absolutePath)
            put("TMPDIR", tmp.absolutePath)
            put("PWD", home.absolutePath)
            put("PATH", "/system/bin:/system/xbin")
        }.map { (k, v) -> "$k=$v" }.toTypedArray()

        val shell = "/system/bin/sh"
        val newSession = TerminalSession(
            shell,
            home.absolutePath,
            arrayOf("-sh"), // argv[0]; the leading '-' makes it a login shell
            env,
            TRANSCRIPT_ROWS,
            this,
        )
        session = newSession
        sessionStartedAt = SystemClock.elapsedRealtime()
        terminalView.attachSession(newSession)
    }

    // ---------------------------------------------------------------- extra keys

    private fun buildExtraKeys(row: LinearLayout) {
        fun key(label: String, onTap: () -> Unit) = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(getColor(R.color.key_text))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            setOnClickListener {
                onTap()
                terminalView.requestFocus()
            }
            row.addView(this)
        }

        key("ESC") { sendKey(KeyEvent.KEYCODE_ESCAPE) }
        key("TAB") { sendKey(KeyEvent.KEYCODE_TAB) }
        ctrlKey = key("CTRL") { ctrlOn = !ctrlOn; refreshModifierKeys() }
        altKey = key("ALT") { altOn = !altOn; refreshModifierKeys() }
        key("-") { session?.write("-") }
        key("/") { session?.write("/") }
        key("←") { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        key("↓") { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        key("↑") { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
        key("→") { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
    }

    /** Lets the terminal encode the key (it knows e.g. the right arrow-key sequence). */
    private fun sendKey(keyCode: Int) {
        var mod = 0
        if (ctrlOn) mod = mod or KeyHandler.KEYMOD_CTRL
        if (altOn) mod = mod or KeyHandler.KEYMOD_ALT
        terminalView.handleKeyCode(keyCode, mod)
        clearModifiers()
    }

    private fun refreshModifierKeys() {
        ctrlKey.setTextColor(getColor(if (ctrlOn) R.color.key_active else R.color.key_text))
        altKey.setTextColor(getColor(if (altOn) R.color.key_active else R.color.key_text))
    }

    private fun clearModifiers() {
        if (!ctrlOn && !altOn) return
        ctrlOn = false
        altOn = false
        refreshModifierKeys()
    }

    // ---------------------------------------------------------------- layout helpers

    /** targetSdk 35 draws edge-to-edge, so pad for the status/nav bars and the keyboard. */
    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun showKeyboard() {
        terminalView.requestFocus()
        getSystemService(InputMethodManager::class.java)
            .showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    // ---------------------------------------------------------------- TerminalSessionClient

    override fun onTextChanged(changedSession: TerminalSession) = terminalView.onScreenUpdated()

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        // Typing `exit` ends the shell; give the user a fresh one. If the shell died
        // straight away, don't loop restarting it — show the error instead.
        if (SystemClock.elapsedRealtime() - sessionStartedAt < 1000) {
            Toast.makeText(this, "Shell exited immediately (status ${finishedSession.exitStatus})", Toast.LENGTH_LONG).show()
            return
        }
        startSession()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Pocket Shell", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip ?: return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        session?.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null

    // ---------------------------------------------------------------- TerminalViewClient

    // A separate object rather than the activity itself: some of its methods (onKeyUp)
    // have the same signature as Activity's own and would silently override them.
    private val viewClient = object : TerminalViewClient {

        /** Pinch to zoom: change the font size in steps, then reset the gesture's scale. */
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                val step = if (scale > 1f) 1 else -1
                fontSizePx = (fontSizePx + step).coerceIn(minFontPx, maxFontPx)
                terminalView.setTextSize(fontSizePx)
                return 1.0f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent) = showKeyboard()

        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
        override fun onLongPress(event: MotionEvent) = false

        // The view may read a modifier more than once for a single key press, so these
        // reads must not change state; the one-shot toggle is cleared after the event.
        override fun readControlKey(): Boolean {
            if (ctrlOn) terminalView.post { clearModifiers() }
            return ctrlOn
        }

        override fun readAltKey(): Boolean {
            if (altOn) terminalView.post { clearModifiers() }
            return altOn
        }

        override fun readShiftKey() = false
        override fun readFnKey() = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false

        override fun onEmulatorSet() {}

        override fun logError(tag: String?, message: String?) = this@MainActivity.logError(tag, message)
        override fun logWarn(tag: String?, message: String?) = this@MainActivity.logWarn(tag, message)
        override fun logInfo(tag: String?, message: String?) = this@MainActivity.logInfo(tag, message)
        override fun logDebug(tag: String?, message: String?) = this@MainActivity.logDebug(tag, message)
        override fun logVerbose(tag: String?, message: String?) = this@MainActivity.logVerbose(tag, message)
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) =
            this@MainActivity.logStackTraceWithMessage(tag, message, e)
        override fun logStackTrace(tag: String?, e: Exception?) = this@MainActivity.logStackTrace(tag, e)
    }

    // ---------------------------------------------------------------- logging

    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "", e) }

    private companion object {
        const val TRANSCRIPT_ROWS = 2000
    }
}
