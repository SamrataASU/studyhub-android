@file:OptIn(ExperimentalMaterial3Api::class)

package com.studyhub.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.studyhub.app.data.Fmt
import com.studyhub.app.data.News
import com.studyhub.app.data.NewsArticle
import com.studyhub.app.data.NewsTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NewsTopic.icon: ImageVector
    get() = when (this) {
        NewsTopic.TOP -> Icons.Filled.Star
        NewsTopic.MARKETS -> Icons.AutoMirrored.Filled.TrendingUp
        NewsTopic.ECONOMY -> Icons.Filled.AccountBalance
        NewsTopic.CORPORATE -> Icons.Filled.Work
        NewsTopic.INVESTING -> Icons.Filled.AttachMoney
    }

@Composable
fun NewsScreen(onOpen: (WebPage) -> Unit, onSettings: () -> Unit) {
    var topicName by rememberSaveable { mutableStateOf(NewsTopic.TOP.name) }
    val topic = NewsTopic.valueOf(topicName)
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(t: NewsTopic) {
        loading = true
        try {
            val fresh = withContext(Dispatchers.IO) { News.fetch(t) }
            if (t.name == topicName) articles = fresh
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Check your internet connection, then pull down to try again."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(topicName) {
        articles = emptyList()
        load(topic)
    }

    val open: (NewsArticle) -> Unit = { onOpen(WebPage(it.title, it.source, it.link)) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("News", onSettings)
        PullToRefreshBox(
            isRefreshing = loading && articles.isNotEmpty(),
            onRefresh = { scope.launch { load(topic) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text("DAILY FINANCE BRIEFING", color = Palette.Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Today's headlines", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("Google News · last 24 hours", color = Palette.Muted, fontSize = 12.sp)
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NewsTopic.entries.forEach { t ->
                            val selected = t == topic
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) Palette.Violet.copy(alpha = 0.85f) else Palette.Violet.copy(alpha = 0.14f))
                                    .clickable { topicName = t.name }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Icon(t.icon, contentDescription = null, tint = if (selected) Color.White else Palette.Violet, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(t.label, color = if (selected) Color.White else Palette.Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (loading && articles.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Palette.Violet)
                        }
                    }
                }
                val err = error
                if (err != null && articles.isEmpty() && !loading) {
                    item { EmptyState(Icons.Filled.WifiOff, "Couldn't load news", err) }
                }
                articles.firstOrNull()?.let { lead -> item(key = "lead-" + lead.id) { LeadStory(lead) { open(lead) } } }
                items(articles.drop(1), key = { it.id }) { a -> NewsRow(a) { open(a) } }
            }
        }
    }
}

@Composable
private fun LeadStory(article: NewsArticle, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Palette.Sky.copy(alpha = 0.9f), Palette.Violet)))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row {
            Text(article.source.uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(Fmt.ago(article.published), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(article.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Read story ›", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NewsRow(article: NewsArticle, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row {
            Text(article.source, color = Palette.Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (article.published != null) Text("  ·  ${Fmt.ago(article.published)}", color = Palette.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(article.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Shows a web page inside the app: the headline on top and the page right below. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPageScreen(page: WebPage, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF15112E))
                .statusBarsPadding()
                .padding(end = 16.dp, top = 4.dp, bottom = 10.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(page.source, color = Palette.Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(page.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (loading) CircularProgressIndicator(color = Palette.Violet, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            // Keep web pages inside the app; never hand off to other apps.
                            return scheme != "http" && scheme != "https"
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                        }
                    }
                    loadUrl(page.url)
                }
            },
        )
    }
}
