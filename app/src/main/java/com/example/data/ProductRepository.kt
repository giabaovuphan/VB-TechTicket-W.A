package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProductRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Vui lòng đăng nhập trước khi thao tác danh mục linh kiện.")

    fun observeProducts(activeOnly: Boolean): Flow<List<ProductItem>> {
        val query = if (activeOnly) {
            db.collection("products").whereEqualTo("active", true)
        } else {
            db.collection("products")
        }
        return query.snapshots().map { snapshot ->
            snapshot.toObjects(ProductItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedWith(
                    compareByDescending<ProductItem> { it.active }
                        .thenBy { it.name.lowercase() }
                )
        }
    }

    suspend fun upsertProduct(
        existingProduct: ProductItem?,
        name: String,
        sku: String?,
        category: String,
        unit: String,
        unitPrice: Long,
        note: String?,
        active: Boolean
    ): Result<String> {
        var docPath = "products"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim()
            val cleanUnit = unit.trim().ifBlank { ProductUnits.first() }
            val cleanSku = sku?.trim()?.takeIf { it.isNotBlank() }?.take(40)
            val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }?.take(300)
            val cleanCategory = category.trim().ifBlank { TicketCategories.first() }.take(60)

            require(cleanName.length in 2..120) { "Tên sản phẩm / linh kiện phải từ 2 đến 120 ký tự." }
            require(cleanUnit.length in 1..30) { "Đơn vị tính không hợp lệ." }
            require(unitPrice in 0L..1_000_000_000L) { "Đơn giá phải từ 0 đến 1.000.000.000 đ." }

            if (existingProduct == null || existingProduct.id.isBlank()) {
                val docRef = db.collection("products").document()
                docPath = docRef.path
                val payload = mapOf(
                    "name" to cleanName,
                    "sku" to cleanSku,
                    "category" to cleanCategory,
                    "unit" to cleanUnit,
                    "unitPrice" to unitPrice,
                    "note" to cleanNote,
                    "active" to active,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = db.collection("products").document(existingProduct.id)
                docPath = docRef.path
                val updates = mutableMapOf<String, Any>(
                    "name" to cleanName,
                    "category" to cleanCategory,
                    "unit" to cleanUnit,
                    "unitPrice" to unitPrice,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanSku != null) {
                    updates["sku"] = cleanSku
                } else if (existingProduct.sku != null) {
                    updates["sku"] = FieldValue.delete()
                }
                if (cleanNote != null) {
                    updates["note"] = cleanNote
                } else if (existingProduct.note != null) {
                    updates["note"] = FieldValue.delete()
                }
                docRef.update(updates).await()
                Result.success(existingProduct.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingProduct == null) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun toggleProductActive(product: ProductItem, active: Boolean): Result<Unit> {
        val docPath = "products/${product.id}"
        return try {
            requireUserId()
            require(product.id.isNotBlank()) { "Mã linh kiện không hợp lệ." }
            db.collection("products").document(product.id).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val docPath = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Mã linh kiện không hợp lệ." }
            db.collection("products").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
