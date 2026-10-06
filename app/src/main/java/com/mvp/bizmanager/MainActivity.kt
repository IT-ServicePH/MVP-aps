package com.mvp.bizmanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var offlineView: View
    private lateinit var retryButton: MaterialButton

    private lateinit var introContainer: View
    private lateinit var introVideoView: VideoView
    private lateinit var skipIntroButton: MaterialButton
    private var isIntroPlaying = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var photoUri: Uri? = null

    private val siteUrl = "https://www.mvp.com.ai/"

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Some permissions were denied. Camera / gallery features may not work.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // File / camera chooser result
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleFileChooserResult(result.resultCode, result.data)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen – must be called before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        offlineView = findViewById(R.id.offlineView)
        retryButton = findViewById(R.id.retryButton)

        introContainer = findViewById(R.id.introContainer)
        introVideoView = findViewById(R.id.introVideoView)
        skipIntroButton = findViewById(R.id.skipIntroButton)

        requestPermissionsIfNeeded()
        setupWebView()
        setupSwipeRefresh()
        setupOfflineRetry()
        setupBackPress()

        if (savedInstanceState != null) {
            introContainer.visibility = View.GONE
            webView.restoreState(savedInstanceState)
        } else {
            setupAndPlayIntro()
            loadSite()
        }
    }

    private fun setupAndPlayIntro() {
        try {
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.intro}")
            introVideoView.setVideoURI(videoUri)
            introContainer.visibility = View.VISIBLE
            isIntroPlaying = true

            introVideoView.setOnCompletionListener {
                dismissIntroVideo()
            }

            introVideoView.setOnErrorListener { _, _, _ ->
                dismissIntroVideo()
                true
            }

            skipIntroButton.setOnClickListener {
                dismissIntroVideo()
            }

            introContainer.setOnClickListener {
                dismissIntroVideo()
            }

            introVideoView.start()
        } catch (e: Exception) {
            dismissIntroVideo()
        }
    }

    private fun dismissIntroVideo() {
        if (!isIntroPlaying && introContainer.visibility == View.GONE) return
        isIntroPlaying = false
        try {
            if (introVideoView.isPlaying) {
                introVideoView.stopPlayback()
            }
        } catch (ignored: Exception) {
        }

        introContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                introContainer.visibility = View.GONE
                introContainer.alpha = 1f
            }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url.host ?: ""

                // Keep navigation inside our domain
                return if (host.contains("mvp.com.ai")) {
                    false // let WebView handle it
                } else {
                    // Open external links in browser
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Cannot open link", Toast.LENGTH_SHORT)
                            .show()
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                hideOffline()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showOffline()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }

            // File chooser (camera + gallery)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(packageManager) != null) {
                    val photoFile = createImageFile()
                    photoFile?.let {
                        photoUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            it
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    }
                }

                val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }

                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, contentIntent)
                    putExtra(Intent.EXTRA_TITLE, "Select or take a photo")
                    if (photoUri != null) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
                    }
                }

                try {
                    fileChooserLauncher.launch(chooser)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "Cannot open file chooser",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                return true
            }
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            null
        }
    }

    private fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = filePathCallback ?: return
        filePathCallback = null

        if (resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return
        }

        val results = when {
            data?.data != null -> arrayOf(data.data!!)
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
            photoUri != null -> arrayOf(photoUri!!)
            else -> null
        }

        callback.onReceiveValue(results)
        photoUri = null
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.purple_primary)
        swipeRefresh.setOnRefreshListener {
            if (isNetworkAvailable()) {
                webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
                showOffline()
                Toast.makeText(this, "Still offline", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOfflineRetry() {
        retryButton.setOnClickListener {
            if (isNetworkAvailable()) {
                hideOffline()
                loadSite()
            } else {
                Toast.makeText(this, "Still offline. Check your connection.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isIntroPlaying) {
                    dismissIntroVideo()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadSite() {
        if (isNetworkAvailable()) {
            hideOffline()
            webView.loadUrl(siteUrl)
        } else {
            showOffline()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showOffline() {
        offlineView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        progressBar.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun hideOffline() {
        offlineView.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        if (isIntroPlaying) {
            try {
                introVideoView.pause()
            } catch (ignored: Exception) {
            }
        }
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (isIntroPlaying && !introVideoView.isPlaying) {
            try {
                introVideoView.start()
            } catch (ignored: Exception) {
            }
        }
        webView.onResume()
    }

    override fun onDestroy() {
        if (isIntroPlaying) {
            try {
                introVideoView.stopPlayback()
            } catch (ignored: Exception) {
            }
        }
        webView.destroy()
        super.onDestroy()
    }
}
