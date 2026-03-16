package com.marketapp.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.marketapp.BuildConfig
import com.marketapp.data.model.Order
import com.marketapp.data.model.Product
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor() {

    companion object {
        private const val TAG = "FirebaseAI"
        private const val SEARCH_CACHE_MAX = 30
    }

    // Normalized query → matched products. Capped at SEARCH_CACHE_MAX entries (FIFO eviction).
    private val searchCache = LinkedHashMap<String, List<Product>>(SEARCH_CACHE_MAX, 0.75f, false)
    private val cacheMutex = Mutex()

    private val textModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-2.0-flash",
            systemInstruction = content { text("Write exactly one friendly sentence under 15 words specific to the products.") }
        )
    }

    private val jsonModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-2.0-flash",
            generationConfig = generationConfig { responseMimeType = "application/json" }
        )
    }

    suspend fun generateOrderMessage(order: Order, orderCount: Int): String =
        runCatching {
            val itemsSummary = order.items.joinToString { "${it.quantity}x ${it.product.title}" }
            if (BuildConfig.DEBUG) Log.d(TAG, "[orderMessage] orderCount=$orderCount items=$itemsSummary")
            val prompt = """
                Items ordered: $itemsSummary
                Payment method: ${order.paymentMethod}
                Customer order number: $orderCount
            """.trimIndent()
            val result = textModel.generateContent(prompt).text.orEmpty().trim()
            if (BuildConfig.DEBUG) Log.d(TAG, "[orderMessage] gemini response: \"$result\"")
            result
        }.onFailure { e ->
            if (BuildConfig.DEBUG) Log.e(TAG, "[orderMessage] gemini failed → empty message", e)
        }.getOrDefault("")

    suspend fun rankProducts(
        products: List<Product>,
        orderHistory: List<Map<String, Any>>
    ): List<Int> = runCatching {
        if (BuildConfig.DEBUG) Log.d(TAG, "[rankProducts] catalog=${products.size} products, history=${orderHistory.size} orders")
        val historySummary = orderHistory.joinToString("\n") { order ->
            val items = (order["items"] as? List<*>)?.joinToString { item ->
                val m = item as? Map<*, *>
                "${m?.get("productName")} (${m?.get("category")})"
            } ?: ""
            "- $items"
        }
        val catalog = products.joinToString("\n") { p ->
            JSONObject().put("id", p.id).put("title", p.title).put("category", p.category).toString()
        }
        val prompt = """
            User's past orders:
            $historySummary

            Product catalog:
            $catalog

            Return a JSON array of product IDs (integers) ranked by relevance to the user's purchase history.
            Only include IDs from the catalog. Example: [3, 7, 1, 12]
        """.trimIndent()
        val text = jsonModel.generateContent(prompt).text.orEmpty().trim()
        if (BuildConfig.DEBUG) Log.d(TAG, "[rankProducts] gemini raw response: $text")
        val arr = JSONArray(text)
        val ranked = (0 until arr.length()).map { arr.getInt(it) }
        if (BuildConfig.DEBUG) Log.d(TAG, "[rankProducts] ranked ${ranked.size} IDs: $ranked")
        ranked
    }.onFailure { e ->
        if (BuildConfig.DEBUG) Log.e(TAG, "[rankProducts] gemini failed → original order kept", e)
    }.getOrDefault(emptyList())

    suspend fun searchProducts(
        query: String,
        products: List<Product>
    ): List<Product>? {
        val key = query.trim().lowercase()
        cacheMutex.withLock { searchCache[key] }?.let { cached ->
            if (BuildConfig.DEBUG) Log.d(TAG, "[search] cache hit for \"$key\" → ${cached.size} results, skipping Gemini")
            return cached
        }
        return runCatching {
            if (BuildConfig.DEBUG) Log.d(TAG, "[search] query=\"$query\" catalog=${products.size} products")
            val catalog = products.joinToString("\n") { p ->
                JSONObject().put("id", p.id).put("title", p.title).put("category", p.category).toString()
            }
            val prompt = """
                Search query: "$query"

                Product catalog:
                $catalog

                The query may be in any language (e.g. Javanese, French, Indonesian, English). Translate it if needed,
                then return a JSON array of product IDs (integers) that semantically match the query.
                Only include IDs from the catalog. If nothing matches, return [].
                Example: [4, 9, 2]
            """.trimIndent()
            val text = jsonModel.generateContent(prompt).text.orEmpty().trim()
            if (BuildConfig.DEBUG) Log.d(TAG, "[search] gemini raw response: $text")
            val arr = JSONArray(text)
            val ids = (0 until arr.length()).map { arr.getInt(it) }.toSet()
            val matched = products.filter { it.id in ids }
            if (BuildConfig.DEBUG) Log.d(TAG, "[search] matched ${matched.size} products: ${matched.map { it.title }}")
            cacheMutex.withLock {
                if (searchCache.size >= SEARCH_CACHE_MAX) searchCache.remove(searchCache.keys.first())
                searchCache[key] = matched
            }
            matched
        }.onFailure { e ->
            if (BuildConfig.DEBUG) Log.e(TAG, "[search] gemini failed → falling back to keyword search", e)
        }.getOrNull()
    }
}
