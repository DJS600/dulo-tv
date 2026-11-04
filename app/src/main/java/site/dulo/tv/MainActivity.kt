package site.dulo.tv

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.app.Activity

class MainActivity : Activity() {

    companion object {
        private const val START_URL = "https://dulo.site/"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var web: WebView
    private lateinit var fullscreen: FrameLayout
    private lateinit var spinner: ProgressBar
    private var nativeMode = false
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
            cacheMode = WebSettings.LOAD_DEFAULT
            // --- the pop-up killers ---
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
                spinner.visibility = View.VISIBLE
                // Patch window.open before the site's own scripts execute.
                view?.evaluateJavascript(TvNav.JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                spinner.visibility = View.GONE
                view?.evaluateJavascript(TvNav.JS, null)
                view?.requestFocus()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (AdBlock.isAdDestination(url)) return true          // dead-end ad redirect
                if (url.startsWith("intent:")) {
                    return try { startActivity(Intent.parseUri(url, 0)); true } catch (_: Exception) { false }
                }
                if (url.startsWith("magnet:")) return true
                return false                                            // load everything else in-place
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (AdBlock.isAd(request.url.toString())) {
                    return WebResourceResponse("text/plain", "utf-8", 200, "OK",
                        mapOf("Cache-Control" to "max-age=86400"),
                        java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    spinner.visibility = View.GONE
                    view.loadDataWithBaseURL(
                        null,
                        "<body style='background:#000;color:#fff;font-family:sans-serif;" +
                            "display:flex;align-items:center;justify-content:center;height:100vh;" +
                            "margin:0;text-align:center'><div><h2>Can't reach dulo.site</h2>" +
                            "<p>Check the connection, then press BACK.</p></div></body>",
                        "text/html", "utf-8", null
                    )
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // No second window is ever created -> no pop-ups.
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean = false

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
        Toast.makeText(this, "D-pad to browse · OK to open · MENU toggles mouse/keyboard mode", Toast.LENGTH_LONG).show()
    }

    inner class Bridge {
        @JavascriptInterface
        fun setNativeMode(on: Boolean) { runOnUiThread { nativeMode = on } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
                nativeMode = !nativeMode
                if (!nativeMode) web.evaluateJavascript("window.__tvrecenter&&window.__tvrecenter()", null)
                Toast.makeText(this,
                    if (nativeMode) "Keyboard/mouse mode" else "D-pad mode",
                    Toast.LENGTH_SHORT).show()
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
        if (!nativeMode && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            return true
        }
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