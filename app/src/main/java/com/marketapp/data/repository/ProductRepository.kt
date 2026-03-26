package com.marketapp.data.repository

import com.marketapp.data.model.Product
import com.marketapp.data.model.Rating
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// ── DummyJSON response models ──────────────────────────────────────────────────
// DummyJSON wraps list results and uses different field names from FakeStore.

data class DummyProductListResponse(val products: List<DummyProduct>)

// Categories come back as objects {slug, name, url} — we use slug as the category key.
data class DummyCategoryItem(val slug: String, val name: String)

data class DummyProduct(
    val id: Int,
    val title: String,
    val price: Double,
    val description: String,
    val category: String,
    val thumbnail: String,  // DummyJSON uses "thumbnail" where FakeStore used "image"
    val rating: Double = 0.0,
    val stock: Int = 0
) {
    fun toProduct() = Product(
        id          = id,
        title       = title,
        price       = price,
        description = description,
        category    = category,
        image       = thumbnail,
        rating      = Rating(rate = rating, count = stock)
    )
}

// ── Retrofit API ───────────────────────────────────────────────────────────────

interface DummyJsonApi {
    @GET("products")
    suspend fun getProducts(
        @Query("limit")  limit:  Int    = 40,
        @Query("sortBy") sortBy: String = "title",
        @Query("order")  order:  String = "asc"
    ): DummyProductListResponse

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: Int): DummyProduct

    @GET("products/categories")
    suspend fun getCategories(): List<DummyCategoryItem>

    @GET("products/category/{category}")
    suspend fun getProductsByCategory(@Path("category") category: String): DummyProductListResponse
}

// ── Repository Interface ───────────────────────────────────────────────────────

interface ProductRepository {
    suspend fun getProducts(limit: Int = 40): Result<List<Product>>
    suspend fun getProduct(id: Int): Result<Product>
    suspend fun getCategories(): Result<List<String>>
    suspend fun getProductsByCategory(category: String): Result<List<Product>>
    suspend fun searchProducts(query: String): Result<List<Product>>
}

// ── Repository Implementation ─────────────────────────────────────────────────

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val api: DummyJsonApi
) : ProductRepository {

    // Simple in-memory cache — replace with Room if offline persistence is needed
    private var cachedProducts: List<Product>? = null
    // Pre-lowercased (title, description, category) aligned by index with cachedProducts.
    // Built once at cache time so searchProducts() never calls lowercase() per-item.
    private var searchIndex: List<Triple<String, String, String>>? = null

    override suspend fun getProducts(limit: Int): Result<List<Product>> = runCatching {
        cachedProducts ?: api.getProducts(limit = limit).products.map { it.toProduct() }.also { primeCache(it) }
    }

    private fun primeCache(products: List<Product>) {
        cachedProducts = products
        searchIndex = products.map {
            Triple(it.title.lowercase(), it.description.lowercase(), it.category.lowercase())
        }
    }

    override suspend fun getProduct(id: Int): Result<Product> = runCatching {
        cachedProducts?.find { it.id == id } ?: api.getProduct(id).toProduct()
    }

    override suspend fun getCategories(): Result<List<String>> = runCatching {
        api.getCategories().map { it.slug }
    }

    override suspend fun getProductsByCategory(category: String): Result<List<Product>> = runCatching {
        cachedProducts?.filter { it.category == category }
            ?: api.getProductsByCategory(category).products.map { it.toProduct() }
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> = runCatching {
        val all = cachedProducts ?: api.getProducts(limit = 40).products.map { it.toProduct() }.also { primeCache(it) }
        val idx = searchIndex ?: return@runCatching all
        val q = query.lowercase()
        all.filterIndexed { i, _ ->
            val s = idx[i]
            s.first.contains(q) || s.second.contains(q) || s.third.contains(q)
        }
    }
}
