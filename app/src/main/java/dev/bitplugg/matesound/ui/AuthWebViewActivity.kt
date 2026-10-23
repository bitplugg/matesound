package dev.bitplugg.matesound.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bitplugg.matesound.MateSoundApp
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.data.auth.AuthUrls
import dev.bitplugg.matesound.data.model.ThemeMode
import dev.bitplugg.matesound.data.remote.AuthRequiredException
import dev.bitplugg.matesound.data.remote.ScApiException
import dev.bitplugg.matesound.ui.theme.MateSoundTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AuthWebViewActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MateSoundApp).container

        setContent {
            val scope = rememberCoroutineScope()
            var webView by remember { mutableStateOf<WebView?>(null) }
            var finished by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf(R.string.login_waiting) }

            fun handleToken(raw: String?) {
                if (finished) return
                val token = AuthUrls.extractToken(raw) ?: return
                finished = true
                status = R.string.login_saving
                scope.launch {
                    container.auth.saveToken(token)
                    val check = runCatching {
                        withTimeout(20_000L) { container.repository.me() }
                    }
                    val failure = check.exceptionOrNull()
                    val rejected = failure is AuthRequiredException ||
                        (failure is ScApiException && failure.code in 401..403)
                    if (rejected) {
                        // Do not keep a token the API refuses, otherwise every
                        // screen shows a misleading "signed in" state.
                        container.auth.logout()
                        status = R.string.login_rejected
                        finished = false
                    } else {
                        CookieManager.getInstance().flush()
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }

            fun checkCookies() {
                if (finished) return
                val urls = listOf(
                    "https://soundcloud.com",
                    "https://m.soundcloud.com",
                    "https://secure.soundcloud.com",
                )
                for (url in urls) {
                    val cookie = CookieManager.getInstance().getCookie(url) ?: continue
                    val token = AuthUrls.tokenFromCookie(cookie) ?: continue
                    handleToken(token)
                    return
                }
            }

            MateSoundTheme(ThemeMode.DARK) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LoginContent(
                        statusRes = status,
                        onClose = { finish() },
                        onCheck = {
                            checkCookies()
                            webView?.evaluateJavascript(AuthUrls.TOKEN_PROBE_JS) { result ->
                                handleToken(result)
                            }
                        },
                        onWebViewReady = { view ->
                            webView = view
                        },
                        onPageFinished = { checkCookies() },
                        onTokenFromUrl = { url ->
                            AuthUrls.tokenFromUrl(url)?.let { handleToken(it) }
                        },
                    )
                }
            }

            LaunchedEffect(webView) {
                while (!finished) {
                    delay(1_500L)
                    val view = webView ?: continue
                    checkCookies()
                    view.evaluateJavascript(AuthUrls.TOKEN_PROBE_JS) { result ->
                        handleToken(result)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginContent(
    statusRes: Int,
    onClose: () -> Unit,
    onCheck: () -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onPageFinished: () -> Unit,
    onTokenFromUrl: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.login_title)) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                }
            },
        )
        Text(
            text = stringResource(R.string.login_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.userAgentString = USER_AGENT
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                onTokenFromUrl(request.url.toString())
                                return false
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                onPageFinished()
                            }
                        }
                        loadUrl(AuthUrls.loginPage())
                    }.also(onWebViewReady)
                },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onCheck) {
                Text(stringResource(R.string.login_check))
            }
        }
    }
}

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
