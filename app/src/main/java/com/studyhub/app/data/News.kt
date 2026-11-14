package com.studyhub.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class NewsArticle(
    val id: String,
    val title: String,
    val source: String,
    val link: String,
    val published: Instant?,
)

/** Finance topics, each from Google News' free RSS search (last 24 hours). */
enum class NewsTopic(val label: String, val query: String) {
    TOP("Top", "finance"),
    MARKETS("Markets", "stock market OR Wall Street OR Nasdaq"),
    ECONOMY("Economy", "economy OR Federal Reserve OR inflation"),
    CORPORATE("Corporate", "earnings OR merger OR IPO"),
    INVESTING("Investing", "investing OR personal finance");

    val feedUrl: String
        get() = "https://news.google.com/rss/search?q=" +
            URLEncoder.encode("$query when:1d", "UTF-8") + "&hl=en-US&gl=US&ceid=US:en"
}

object News {
    /** Call off the main thread. */
    fun fetch(topic: NewsTopic): List<NewsArticle> {
        val conn = URL(topic.feedUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.useCaches = false
        val xml = try {
            if (conn.responseCode !in 200..299) throw IOException("News is unavailable right now.")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val seen = HashSet<String>()
        return parse(xml)
            .filter { seen.add(it.title.lowercase()) }
            .sortedByDescending { it.published ?: Instant.EPOCH }
    }

    private fun parse(xml: String): List<NewsArticle> {
        val out = mutableListOf<NewsArticle>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var inItem = false
        var text = StringBuilder()
        var title = ""
        var link = ""
        var pub = ""
        var source = ""
        var guid = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "item") {
                        inItem = true
                        title = ""; link = ""; pub = ""; source = ""; guid = ""
                    }
                    text = StringBuilder()
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (inItem) {
                        val v = text.toString().trim()
                        when (parser.name) {
                            "title" -> title = v
                            "link" -> link = v
                            "pubDate" -> pub = v
                            "source" -> source = v
                            "guid" -> guid = v
                            "item" -> {
                                inItem = false
                                if (title.isNotEmpty() && link.isNotEmpty()) {
                                    val suffix = " - $source"
                                    val headline = if (source.isNotEmpty() && title.endsWith(suffix)) title.dropLast(suffix.length) else title
                                    out.add(NewsArticle(guid.ifEmpty { link }, headline, source, link, parseDate(pub)))
                                }
                            }
                        }
                    }
                    text = StringBuilder()
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseDate(s: String): Instant? =
        runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
}
