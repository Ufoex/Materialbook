package com.eepiemi.materialbook.ui.screens

import android.view.View
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eepiemi.materialbook.utils.ShareBox
import com.eepiemi.materialbook.utils.jsBridge.ShareBridge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.zIndex
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.DESKTOP_USER_AGENT
import com.eepiemi.materialbook.utils.FullscreenController
import com.eepiemi.materialbook.utils.MessagesLayerRoute
import com.eepiemi.materialbook.utils.appWebViewParams
import com.eepiemi.materialbook.utils.jsBridge.ClipboardBridge
import com.eepiemi.materialbook.utils.jsBridge.DownloadBridge
import com.eepiemi.materialbook.utils.jsBridge.MaterialYouBridge
import com.eepiemi.materialbook.utils.jsBridge.MessagesBridge
import com.eepiemi.materialbook.utils.messagesLayerRoute
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState

/**
 * Messages in desktop mode: the desktop Messages page in its own WebView, drawn over the
 * main one. The main view keeps its mobile page (and scroll position) underneath, so
 * closing the layer goes straight back to where the user was.
 *
 * - Back steps through the layer's own history (a conversation back to the inbox), then
 *   closes the layer.
 * - A regular Facebook page opened from here (a profile, the home page) closes the layer
 *   and opens in the main view instead: [onOpenInMain].
 * - Non-Facebook links go to [onExternalUrl], as in the main view.
 */
@Composable
fun MessagesLayer(
    url: String,
    userScripts: String?,
    fullscreen: FullscreenController,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
    background: Color,
    primaryColor: Int,
    onPrimaryColor: Int,
    onClose: () -> Unit,
    onOpenInMain: (String) -> Unit,
    onExternalUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val currentOnOpenInMain by rememberUpdatedState(onOpenInMain)
    val currentOnExternalUrl by rememberUpdatedState(onExternalUrl)

    // The user agent is applied by the library when it creates the WebView, before the
    // first load (setting it is idempotent, so doing it on every composition is fine).
    val state = rememberWebViewState(url).also {
        it.webSettings.customUserAgentString = DESKTOP_USER_AGENT
    }
    val navigator = rememberWebViewNavigator(
        requestInterceptor = object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator
            ): WebRequestInterceptResult =
                when (val route = messagesLayerRoute(request.url, request.isForMainFrame)) {
                    MessagesLayerRoute.Allow -> WebRequestInterceptResult.Allow
                    is MessagesLayerRoute.Remap -> {
                        navigator.loadUrl(route.url)
                        WebRequestInterceptResult.Reject
                    }
                    is MessagesLayerRoute.OpenInMain -> {
                        currentOnOpenInMain(route.url)
                        WebRequestInterceptResult.Reject
                    }
                    is MessagesLayerRoute.External -> {
                        currentOnExternalUrl(route.url)
                        WebRequestInterceptResult.Reject
                    }
                }
        }
    )

    // Composed after the main view's handlers, so it gets Back first; disabled while a
    // video is fullscreen, so Back leaves fullscreen through the main view's handler.
    BackHandler(enabled = !isFullscreen) {
        // Asked of the WebView itself: opening a conversation is an in-page navigation,
        // which navigator.canGoBack doesn't always pick up.
        val canGoBack = runCatching { state.nativeWebView.canGoBack() }.getOrDefault(false)
        if (canGoBack) navigator.navigateBack() else onClose()
    }

    // Same page scripts as the main view (download hook, theme, ...), plus the layer's own
    // in-page navigation watcher, loaded from the bundled resource like the PiP detector.
    val loadingState = state.loadingState
    LaunchedEffect(loadingState, userScripts) {
        if (loadingState is LoadingState.Finished) {
            val layerScript = resources.openRawResource(R.raw.messages_layer)
                .bufferedReader().use { it.readText() }
            val shareScript = resources.openRawResource(R.raw.share_into_chat)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript((userScripts ?: "") + "\n" + layerScript + "\n" + shareScript) {}
        }
    }

    Box(modifier = modifier.zIndex(1F).background(background)) {
        WebView(
            modifier = Modifier.fillMaxSize(),
            state = state,
            navigator = navigator,
            platformWebViewParams = appWebViewParams(fullscreen),
            captureBackPresses = false,
            // The layer is gone for good once closed: free its WebView (and its page).
            onDispose = { webView -> webView.destroy() },
            onCreated = { webView ->
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                state.webSettings.apply {
                    isJavaScriptEnabled = true
                    androidWebSettings.apply {
                        domStorageEnabled = true
                        hideDefaultVideoPoster = true
                        mediaPlaybackRequiresUserGesture = false
                    }
                }
                webView.apply {
                    addJavascriptInterface(ShareBridge(context) { webView.post { ShareBox.inChat = null } }, "ShareBridge")
                    addJavascriptInterface(DownloadBridge(context), "DownloadBridge")
                    addJavascriptInterface(ClipboardBridge(context), "ClipboardBridge")
                    addJavascriptInterface(MaterialYouBridge(primaryColor, onPrimaryColor), "MaterialYouBridge")
                    addJavascriptInterface(
                        // Called on the JavaBridge thread: hop to the UI thread first.
                        MessagesBridge { leftTo -> webView.post { currentOnOpenInMain(leftTo) } },
                        "MessagesBridge"
                    )
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                }
            }
        )
        // Sending something into a chat: pick the chat (or it is already open) and it is typed in.
        if (ShareBox.inChat != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.share_pick_chat),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = { ShareBox.inChat = null }) { Text(stringResource(R.string.share_cancel)) }
                }
            }
        }
        if (loadingState is LoadingState.Loading) {
            LinearProgressIndicator(
                progress = { loadingState.progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            )
        }
    }
}
