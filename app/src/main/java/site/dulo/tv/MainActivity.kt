package site.dulo.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val TAG = "DuloTV"
        private const val START_URL = "https://dulo.site/"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
        private const val GESTURE_WINDOW_MS = 1200L
    }

    private lateinit var web: WebView
    private lateinit var fullscreen: FrameLayout
    private lateinit var spinner: ProgressBar
    private var nativeMode = false
    private var lastInputAt = 0L
    private var menuDownAt = 0L
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()

        web = findViewById(R.id.web)
        fullscreen = findViewById(R.id.fullscreenContainer)
        spinner = findViewById(R.id.spinner)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = DESKTOP_UA
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = true
            allowContentAccess = true
        }
        web.setBackgroundColor(Color.BLACK)
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.addJavascriptInterface(Bridge(), "__tvbridge")

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.d(TAG, "start: $url")
                spinner.visibility = View.VISIBLE
                view?.evaluateJavascript(TvNav.JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "done: $url")
                spinner.visibility = View.GONE
                view?.evaluateJavascript(TvNav.JS, null)
                view?.requestFocus()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                val target = request.url.host?.lowercase() ?: return false
                val current = view.url?.let { Uri.parse(it).host?.lowercase() } ?: ""

                if (url.startsWith("intent:")) {
                    return try { startActivity(Intent.parseUri(url, 0)); true }
                    catch (_: Exception) { false }
                }
                if (url.startsWith("magnet:")) return true

                if (AdBlock.isAdDestination(url)) {
                    Log.d(TAG, "blocked ad nav: $url")
                    return true
                }

                // Script-driven redirect (no recent input) to an unrelated host.
                val sinceInput = SystemClock.elapsedRealtime() - lastInputAt
                val sameSite = sameRegistrable(current, target)
                if (sinceInput > GESTURE_WINDOW_MS && !sameSite && !AdBlock.isAllowedNav(url)) {
                    Log.d(TAG, "blocked auto nav: $url")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (AdBlock.isAd(request.url.toString())) {
                    return WebResourceResponse(
                        "text/plain", "utf-8", 200, "OK",
                        mapOf("Cache-Control" to "max-age=86400"),
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    spinner.visibility = View.GONE
                    Log.d(TAG, "error: ${error.description} $request")
                    view.loadDataWithBaseURL(
                        null,
                        "<body style='background:#000;color:#fff;font-family:sans-serif;" +
                            "display:flex;align-items:center;justify-content:center;height:100vh;" +
                            "margin:0;text-align:center'><div><h2>Can't reach dulo.site</h2>" +
                            "<p>Press BACK, then MENU to retry.</p></div></body>",
                        "text/html", "utf-8", null
                    )
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
            ): Boolean = false

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d(TAG, "js: ${m.message()} @${m.lineNumber()}")
                return true
            }

            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = v
                customCallback = callback
                fullscreen.addView(v, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                fullscreen.visibility = View.VISIBLE
                web.visibility = View.INVISIBLE
                goImmersive()
            }

            override fun onHideCustomView() {
                customView?.let { fullscreen.removeView(it) }
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                fullscreen.visibility = View.GONE
                web.visibility = View.VISIBLE
                web.requestFocus()
                goImmersive()
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                spinner.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        web.loadUrl(START_URL)
        Toast.makeText(this, "OK opens · BACK returns · MENU mode · hold MENU to nuke overlays", Toast.LENGTH_LONG).show()
    }

    private fun sameRegistrable(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return a.substringAfterLast('.') == b.substringAfterLast('.') &&
               a.substringBeforeLast('.').substringAfterLast('.') ==
               b.substringBeforeLast('.').substringAfterLast('.')
    }

    inner class Bridge {
        @JavascriptInterface
        fun setNativeMode(on: Boolean) { runOnUiThread { nativeMode = on } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastInputAt = SystemClock.elapsedRealtime()

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                web.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');" +
                    "if(v){v.paused?v.play():v.pause();}})()", null)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (event.repeatCount == 0) menuDownAt = SystemClock.elapsedRealtime()
                return true
            }
        }

        if (nativeMode) return super.onKeyDown(keyCode, event)

        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "0,-1"
            KeyEvent.KEYCODE_DPAD_DOWN -> "0,1"
            KeyEvent.KEYCODE_DPAD_LEFT -> "-1,0"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "1,0"
            else -> null
        }
        if (dir != null) {
            web.evaluateJavascript("window.__tvgo&&window.__tvgo($dir)", null)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            web.evaluateJavascript("window.__tvok&&window.__tvok()", null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val held = SystemClock.elapsedRealtime() - menuDownAt
            if (held > 450L) {
                web.evaluateJavascript("window.__tvnuke&&window.__tvnuke()", null)
                Toast.makeText(this, "Overlays nuked", Toast.LENGTH_SHORT).show()
            } else {
                nativeMode = !nativeMode
                if (!nativeMode) web.evaluateJavascript(
                    "window.__tvrecenter&&window.__tvrecenter()", null)
                Toast.makeText(this,
                    if (nativeMode) "Keyboard mode" else "D-pad mode", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        if (!nativeMode && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER))
            return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (customView != null) { web.webChromeClient?.onHideCustomView(); return }
        if (nativeMode) { nativeMode = false; return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    private fun goImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume(); goImmersive() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}