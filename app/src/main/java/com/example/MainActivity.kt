package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ArushiMainScreen(
                        modifier = Modifier.padding(innerPadding),
                        activity = this,
                        scope = activityScope
                    )
                }
            }
        }
    }
}

@Composable
fun ArushiMainScreen(modifier: Modifier = Modifier, activity: ComponentActivity, scope: CoroutineScope) {
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                         permissions[Manifest.permission.READ_CONTACTS] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidWebViewContainer(activity = activity, scope = scope)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AndroidWebViewContainer(activity: ComponentActivity, scope: CoroutineScope) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val apiKey = BuildConfig.GEMINI_API_KEY
                        evaluateJavascript("window.GEMINI_API_KEY = '$apiKey';", null)
                    }
                }

                addJavascriptInterface(AndroidBridge(context, scope), "AndroidBridge")
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}

class AndroidBridge(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private fun showToast(msg: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun openWhatsApp() {
        scope.launch(Dispatchers.Main) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) {
                    context.startActivity(intent)
                    showToast("Opened WhatsApp")
                } else {
                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))
                    context.startActivity(fallback)
                    showToast("WhatsApp not installed, opened web link")
                }
            } catch (e: Exception) {
                showToast("Failed to open WhatsApp: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun openApp(appName: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val lower = appName.lowercase().trim()
                val pkg = when {
                    lower.contains("whatsapp") -> "com.whatsapp"
                    lower.contains("youtube") -> "com.google.android.youtube"
                    lower.contains("instagram") -> "com.instagram.android"
                    lower.contains("chrome") -> "com.android.chrome"
                    lower.contains("settings") -> {
                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        showToast("Opened Settings")
                        return@launch
                    }
                    else -> null
                }
                if (pkg != null) {
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        context.startActivity(intent)
                        showToast("Opened $appName")
                        return@launch
                    }
                }
                val intent = context.packageManager.getLaunchIntentForPackage(appName)
                if (intent != null) {
                    context.startActivity(intent)
                    showToast("Opened $appName")
                } else {
                    showToast("App $appName not found")
                }
            } catch (e: Exception) {
                showToast("Error opening app: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                context.startActivity(intent)
                showToast("Opened URL")
            } catch (e: Exception) {
                showToast("Failed to open URL: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun makeCall(phoneNumber: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                context.startActivity(intent)
                showToast("Calling $phoneNumber")
            } catch (e: Exception) {
                showToast("Failed to make call: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun callContact(contactName: String): String {
        try {
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )

            val matches = mutableListOf<Pair<String, String>>()
            cursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val num = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    if (name.isNotEmpty() && num.isNotEmpty()) {
                        matches.add(Pair(name, num))
                    }
                }
            }

            if (matches.isEmpty()) {
                showToast("Contact '$contactName' not found.")
                return "NOT_FOUND: $contactName"
            } else if (matches.size == 1) {
                val (name, num) = matches[0]
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                context.startActivity(intent)
                showToast("Calling $name ($num)")
                return "SUCCESS: Called $name at $num"
            } else {
                val names = matches.joinToString(", ") { "${it.first} (${it.second})" }
                showToast("Multiple contacts found for '$contactName'")
                return "MULTIPLE_MATCHES: Found multiple contacts for $contactName: $names"
            }
        } catch (e: Exception) {
            showToast("Error searching contacts: ${e.message}")
            return "ERROR: ${e.message}"
        }
    }

    @JavascriptInterface
    fun isNativeBridgeAvailable(): Boolean = true
}
