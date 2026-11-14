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

/** Finance topics from Google News' free RSS feeds. */
enum class NewsTopic(val label: String, val query: String) {
    TOP("Top", "\"stock market\" OR \"Wall Street\" OR economy OR \"Federal Reserve\""),
    MARKETS("Markets", "\"stock market\" OR \"Wall Street\" OR \"Dow Jones\" OR Nasdaq OR stocks"),
    ECONOMY("Economy", "\"Federal Reserve\" OR inflation OR \"interest rates\" OR \"US economy\" OR jobs report"),
    CORPORATE("Corporate", "earnings OR merger OR acquisition OR IPO OR CEO"),
    INVESTING("Investing", "investing OR \"personal finance\" OR retirement OR \"index funds\"");

    /** Recent news (last few days). Top also uses Google's edited Business page. */
    val feedUrls: List<String>
        get() = if (this == TOP) listOf(BUSINESS_HEADLINES, search("$query when:3d")) else listOf(search("$query when:3d"))

    /** Used if the recent search comes back empty. */
    val fallbackUrl: String get() = search(query)

    companion object {
        const val BUSINESS_HEADLINES =
            "https://news.google.com/rss/topics/CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TVdZU0FtVnVHZ0pWVXlnQVAB?hl=en-US&gl=US&ceid=US:en"

        fun search(q: String) = "https://news.google.com/rss/search?q=" +
            URLEncoder.encode(q, "UTF-8") + "&hl=en-US&gl=US&ceid=US:en"
    }
}

object News {
    /** Publishers that mostly post filings, stock-ticker alerts or press releases. */
    private val blockedSources = listOf(
        "marketbeat", "stock titan", "stocktitan", "tipranks", "simply wall st", "insider monkey", "gurufocus",
        "defense world", "etf daily news", "ticker report", "globenewswire", "pr newswire", "prnewswire",
        "business wire", "businesswire", "accesswire", "newsfile", "sec.gov", "stocks telegraph",
        "investorsobserver", "marketscreener", "zacks", "the lincolnian", "dailypolitical", "modern readers",
    )

    /** Headlines that are filings, ticker spam or stock-quote pages, not news. */
    private val junk = Regex(
        """\b(8-K|10-K|10-Q|S-1|13F|13G|13D|Form 4|SEC filing|proxy statement)\b|\((NYSE|NASDAQ|NYSEARCA|NYSEAMERICAN|OTC|OTCMKTS|TSX|AMEX|LON|BATS)\s*:|shares (sold|bought|acquired|purchased) by|(acquires|buys|sells|trims|boosts|raises|lowers|cuts|reduces|increases|grows|takes) (a |its )?(new )?(position|stake|holdings)|price target|stock price|stock quote|declares (quarterly |monthly )?dividend""",
        RegexOption.IGNORE_CASE,
    )

    /** Call off the main thread. */
    fun fetch(topic: NewsTopic): List<NewsArticle> {
        val all = mutableListOf<NewsArticle>()
        var lastError: Exception? = null
        for (url in topic.feedUrls) {
            try { all += load(url) } catch (e: Exception) { lastError = e }
        }
        if (clean(all).isEmpty()) {
            try { all += load(topic.fallbackUrl) } catch (e: Exception) { lastError = e }
        }
        val result = clean(all)
        if (result.isEmpty() && lastError != null) throw lastError
        return result
    }

    private fun load(url: String): List<NewsArticle> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) StudyHub")
        val xml = try {
            if (conn.responseCode !in 200..299) throw IOException("News is unavailable right now.")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return parse(xml)
    }

    private fun isQuality(a: NewsArticle): Boolean {
        val src = a.source.lowercase()
        if (blockedSources.any { src.contains(it) }) return false
        if (a.title.length < 25) return false
        return !junk.containsMatchIn(a.title)
    }

    /** Newest first, last 4 days, no duplicates, at most 4 stories per publisher. */
    private fun clean(list: List<NewsArticle>): List<NewsArticle> {
        val cutoff = Instant.now().minusSeconds(4 * 86_400L)
        val seen = HashSet<String>()
        val perSource = HashMap<String, Int>()
        val out = mutableListOf<NewsArticle>()
        for (a in list.sortedByDescending { it.published ?: Instant.EPOCH }) {
            val d = a.published
            if (d != null && d.isBefore(cutoff)) continue
            if (!isQuality(a)) continue
            if (!seen.add(a.title.lowercase().take(60)) || !seen.add("id:" + a.id)) continue
            val n = perSource[a.source] ?: 0
            if (n >= 4) continue
            perSource[a.source] = n + 1
            out += a
            if (out.size >= 60) break
        }
        return out
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

/**
 * Finds a picture for the top story. Google News feeds have no images,
 * so this looks the headline up in Bing News, which includes thumbnails.
 */
object NewsImages {
    private val urls = HashMap<String, String>()

    /** Call off the main thread. Returns the picture's web address, or null. */
    fun imageUrl(article: NewsArticle): String? {
        urls[article.id]?.let { return it }
        return try {
            val url = "https://www.bing.com/news/search?q=" + URLEncoder.encode(article.title, "UTF-8") + "&format=rss"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) StudyHub")
            val xml = try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val wanted = words(article.title)
            for ((title, image) in parse(xml)) {
                // Only use the picture if it's clearly the same story.
                val overlap = wanted.intersect(words(title)).size
                if (wanted.isEmpty() || overlap.toDouble() / wanted.size < 0.4) continue
                var raw = image.replace("http://", "https://")
                if (raw.contains("bing.com/th")) raw += "&w=700&h=394&c=7"
                urls[article.id] = raw
                return raw
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the picture (call off the main thread). */
    fun download(url: String): android.graphics.Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000
        try {
            conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun words(s: String): Set<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 3 }.toSet()

    private fun parse(xml: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var inItem = false
        var text = StringBuilder()
        var title = ""
        var image = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "item") { inItem = true; title = ""; image = "" }
                    text = StringBuilder()
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (inItem) {
                        val v = text.toString().trim()
                        when (parser.name) {
                            "title" -> title = v
                            "News:Image", "Image" -> if (image.isEmpty()) image = v
                            "item" -> {
                                inItem = false
                                if (image.isNotEmpty()) out += title to image
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
}
