package com.marketapp.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.marketapp.data.model.Order
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun saveOrder(userId: String, order: Order) {
        val data = mapOf(
            "id"            to order.id,
            "paymentMethod" to order.paymentMethod,
            "totalValue"    to order.totalValue,
            "totalValueIdr" to order.items.sumOf { it.product.priceIdr * it.quantity },
            "status"        to order.status.name,
            "timestamp"     to order.timestamp,
            "items"         to order.items.map { item ->
                mapOf(
                    "productId"   to item.product.id,
                    "productName" to item.product.title,
                    "price"       to item.product.price,
                    "priceIdr"    to item.product.priceIdr,
                    "quantity"    to item.quantity,
                    "category"    to item.product.category,
                    "image"       to item.product.image
                )
            }
        )
        runCatching {
            firestore.collection("users")
                .document(userId)
                .collection("orders")
                .document(order.id)
                .set(data)
                .await()
        }.onFailure { e ->
            Log.e("OrderRepository", "Failed to save order ${order.id}", e)
        }
    }

    suspend fun getRecentOrders(userId: String, limit: Int = 5): List<Map<String, Any>> =
        runCatching {
            firestore.collection("users")
                .document(userId)
                .collection("orders")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .documents
                .mapNotNull { it.data }
        }.getOrDefault(emptyList())
}
