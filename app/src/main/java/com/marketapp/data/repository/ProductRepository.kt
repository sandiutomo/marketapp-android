package com.marketapp.data.repository

import com.marketapp.data.model.Product
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// ── Retrofit API ──────────────────────────────────────────────────────────────

interface FakeStoreApi {
    @GET("products")
    suspend fun getProducts(
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "asc"
    ): List<Product>

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: Int): Product

    @GET("products/categories")
    suspend fun getCategories(): List<String>

    @GET("products/category/{category}")
    suspend fun getProductsByCategory(@Path("category") category: String): List<Product>
}

// ── Repository Interface ───────────────────────────────────────────────────────

interface ProductRepository {
    suspend fun getProducts(limit: Int = 20): Result<List<Product>>
    suspend fun getProduct(id: Int): Result<Product>
    suspend fun getCategories(): Result<List<String>>
    suspend fun getProductsByCategory(category: String): Result<List<Product>>
    suspend fun searchProducts(query: String): Result<List<Product>>
}

// ── Repository Implementation ─────────────────────────────────────────────────

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val api: FakeStoreApi
) : ProductRepository {

    // Simple in-memory cache — replace with Room if offline persistence is needed
    private var cachedProducts: List<Product>? = null

    override suspend fun getProducts(limit: Int): Result<List<Product>> = runCatching {
        cachedProducts ?: api.getProducts(limit).also { cachedProducts = it }
    }

    override suspend fun getProduct(id: Int): Result<Product> = runCatching {
        cachedProducts?.find { it.id == id } ?: api.getProduct(id)
    }

    override suspend fun getCategories(): Result<List<String>> = runCatching {
        api.getCategories()
    }

    override suspend fun getProductsByCategory(category: String): Result<List<Product>> = runCatching {
        cachedProducts?.filter { it.category == category }
            ?: api.getProductsByCategory(category)
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> = runCatching {
        val all = cachedProducts ?: api.getProducts(100).also { cachedProducts = it }
        val q = query.lowercase()
        all.filter {
            it.title.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.lowercase().contains(q)
        }
    }
}
