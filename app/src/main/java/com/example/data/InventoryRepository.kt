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

data class SeedSummary(
    val inventoryCreated: Int,
    val productsCreated: Int,
    val ticketsCreated: Int
)

class InventoryRepository(
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
            ?: throw IllegalStateException("Vui lòng đăng nhập trước khi thao tác kho linh kiện.")

    fun observeInventory(activeOnly: Boolean): Flow<List<InventoryItem>> {
        val query = if (activeOnly) {
            db.collection("inventory").whereEqualTo("active", true)
        } else {
            db.collection("inventory")
        }
        return query.snapshots().map { snapshot ->
            snapshot.toObjects(InventoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedWith(
                    compareByDescending<InventoryItem> { it.active }
                        .thenBy {
                            when (it.alertLevel) {
                                StockAlertLevel.OUT -> 0
                                StockAlertLevel.LOW -> 1
                                StockAlertLevel.OK -> 2
                            }
                        }
                        .thenBy { it.name.lowercase() }
                )
        }
    }

    suspend fun upsertInventoryItem(
        existing: InventoryItem?,
        name: String,
        sku: String?,
        productId: String?,
        category: String,
        unit: String,
        unitPrice: Long,
        quantityOnHand: Long,
        minThreshold: Long,
        location: String?,
        note: String?,
        active: Boolean
    ): Result<String> {
        var docPath = "inventory"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim()
            val cleanUnit = unit.trim().ifBlank { ProductUnits.first() }.take(30)
            val cleanSku = sku?.trim()?.takeIf { it.isNotBlank() }?.take(40)
            val cleanProductId = productId?.trim()?.takeIf { it.isNotBlank() }?.take(128)
            val cleanCategory = category.trim().ifBlank { TicketCategories.first() }.take(60)
            val cleanLocation = location?.trim()?.takeIf { it.isNotBlank() }?.take(80)
            val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }?.take(300)

            require(cleanName.length in 2..120) { "Tên vật tư / linh kiện phải từ 2 đến 120 ký tự." }
            require(cleanUnit.length in 1..30) { "Đơn vị tính không hợp lệ." }
            require(unitPrice in 0L..1_000_000_000L) { "Đơn giá phải từ 0 đến 1.000.000.000 đ." }
            require(quantityOnHand in 0L..1_000_000L) { "Số lượng tồn kho phải từ 0 đến 1.000.000." }
            require(minThreshold in 0L..100_000L) { "Ngưỡng cảnh báo hết hàng phải từ 0 đến 100.000." }

            if (existing == null || existing.id.isBlank()) {
                val docRef = db.collection("inventory").document()
                docPath = docRef.path
                @Suppress("UNCHECKED_CAST")
                val payload = mapOf(
                    "name" to cleanName,
                    "sku" to cleanSku,
                    "productId" to cleanProductId,
                    "category" to cleanCategory,
                    "unit" to cleanUnit,
                    "unitPrice" to unitPrice,
                    "quantityOnHand" to quantityOnHand,
                    "minThreshold" to minThreshold,
                    "location" to cleanLocation,
                    "note" to cleanNote,
                    "active" to active,
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = db.collection("inventory").document(existing.id)
                docPath = docRef.path
                val updates = mutableMapOf<String, Any>(
                    "name" to cleanName,
                    "category" to cleanCategory,
                    "unit" to cleanUnit,
                    "unitPrice" to unitPrice,
                    "quantityOnHand" to quantityOnHand,
                    "minThreshold" to minThreshold,
                    "active" to active,
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanSku != null) updates["sku"] = cleanSku else if (existing.sku != null) updates["sku"] = FieldValue.delete()
                if (cleanProductId != null) updates["productId"] = cleanProductId else if (existing.productId != null) updates["productId"] = FieldValue.delete()
                if (cleanLocation != null) updates["location"] = cleanLocation else if (existing.location != null) updates["location"] = FieldValue.delete()
                if (cleanNote != null) updates["note"] = cleanNote else if (existing.note != null) updates["note"] = FieldValue.delete()
                docRef.update(updates).await()
                Result.success(existing.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existing == null) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun adjustStock(item: InventoryItem, delta: Long): Result<Long> {
        val docPath = "inventory/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Mã vật tư không hợp lệ." }
            val docRef = db.collection("inventory").document(item.id)
            val newQty = db.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                val current = (snap.get("quantityOnHand") as? Number)?.toLong() ?: 0L
                val updated = current + delta
                if (updated < 0L) {
                    throw IllegalStateException("Số lượng tồn kho của '${item.name}' không thể nhỏ hơn 0.")
                }
                if (updated > 1_000_000L) {
                    throw IllegalStateException("Số lượng tồn kho vượt quá giới hạn cho phép.")
                }
                transaction.update(
                    docRef,
                    mapOf(
                        "quantityOnHand" to updated,
                        "updatedByUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                updated
            }.await()
            Result.success(newQty)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleActive(item: InventoryItem, active: Boolean): Result<Unit> {
        val docPath = "inventory/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Mã vật tư không hợp lệ." }
            db.collection("inventory").document(item.id).update(
                mapOf(
                    "active" to active,
                    "updatedByUid" to uid,
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

    suspend fun deleteInventoryItem(inventoryId: String): Result<Unit> {
        val docPath = "inventory/$inventoryId"
        return try {
            requireUserId()
            require(inventoryId.isNotBlank()) { "Mã vật tư không hợp lệ." }
            db.collection("inventory").document(inventoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Idempotently seeds starter records in `products`, `inventory`, and `tickets`
     * so an Admin can immediately test the full Firestore workflow.
     */
    suspend fun seedStarterDatabase(admin: UserAccount): Result<SeedSummary> {
        return try {
            val uid = requireUserId()
            require(admin.parsedRole == UserRole.ADMIN) {
                "Chỉ Quản trị viên mới có quyền khởi tạo dữ liệu mẫu."
            }
            val adminName = admin.displayName.ifBlank { "Quản trị viên" }.take(100)
            val adminEmail = (auth.currentUser?.email ?: admin.email).lowercase().take(150)

            val starterProducts = listOf(
                "prod_starter_pump" to mapOf(
                    "name" to "Bơm áp suất máy pha cà phê Ulka EX5",
                    "sku" to "LK-BOM-EX5",
                    "category" to "Phần cứng",
                    "unit" to "Cái",
                    "unitPrice" to 650_000L,
                    "note" to "Bảo hành 6 tháng, tương thích dòng máy pha 1-2 group",
                    "active" to true,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "prod_starter_gasket" to mapOf(
                    "name" to "Gioăng cao su họng pha silicone 8.5mm",
                    "sku" to "LK-GIOANG-85",
                    "category" to "Phần cứng",
                    "unit" to "Cái",
                    "unitPrice" to 95_000L,
                    "note" to "Thay thế định kỳ 6 tháng",
                    "active" to true,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "prod_starter_ssd" to mapOf(
                    "name" to "SSD NVMe 512GB máy POS thu ngân",
                    "sku" to "LK-SSD-512",
                    "category" to "Thiết bị ngoại vi",
                    "unit" to "Cái",
                    "unitPrice" to 1_150_000L,
                    "note" to "Tốc độ cao cho hệ thống bán hàng",
                    "active" to true,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val starterInventory = listOf(
                "inv_starter_pump" to mapOf(
                    "name" to "Bơm áp suất máy pha cà phê Ulka EX5",
                    "sku" to "INV-BOM-EX5",
                    "productId" to "prod_starter_pump",
                    "category" to "Phần cứng",
                    "unit" to "Cái",
                    "unitPrice" to 650_000L,
                    "quantityOnHand" to 8L,
                    "minThreshold" to 3L,
                    "location" to "Kệ A1 - Kho HCM",
                    "note" to "Linh kiện thay thế máy pha chuẩn",
                    "active" to true,
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "inv_starter_gasket" to mapOf(
                    "name" to "Gioăng cao su họng pha silicone 8.5mm",
                    "sku" to "INV-GIOANG-85",
                    "productId" to "prod_starter_gasket",
                    "category" to "Phần cứng",
                    "unit" to "Cái",
                    "unitPrice" to 95_000L,
                    "quantityOnHand" to 2L,
                    "minThreshold" to 5L,
                    "location" to "Kệ A2 - Kho HCM",
                    "note" to "Sắp hết - cần nhập thêm đợt tới",
                    "active" to true,
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "inv_starter_ssd" to mapOf(
                    "name" to "SSD NVMe 512GB máy POS thu ngân",
                    "sku" to "INV-SSD-512",
                    "productId" to "prod_starter_ssd",
                    "category" to "Thiết bị ngoại vi",
                    "unit" to "Cái",
                    "unitPrice" to 1_150_000L,
                    "quantityOnHand" to 5L,
                    "minThreshold" to 2L,
                    "location" to "Tủ B1 - Thiết bị POS",
                    "active" to true,
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "inv_starter_burr" to mapOf(
                    "name" to "Bộ lưỡi dao máy xay cà phê 64mm",
                    "sku" to "INV-DAO-64",
                    "category" to "Phần cứng",
                    "unit" to "Bộ",
                    "unitPrice" to 780_000L,
                    "quantityOnHand" to 0L,
                    "minThreshold" to 2L,
                    "location" to "Kệ A3 - Kho HCM",
                    "note" to "Đang chờ nhập kho mới",
                    "active" to true,
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val starterTickets = listOf(
                "tk_starter_01" to mapOf(
                    "ticketCode" to "TK-9001",
                    "title" to "Máy pha cà phê áp suất yếu khi chiết xuất",
                    "description" to "Quán Tân Thới Hiệp báo đồng hồ áp suất chỉ lên 4 bar, nước chảy chậm và có tiếng ồn bơm.",
                    "category" to "Phần cứng",
                    "priority" to TicketPriority.HIGH.name,
                    "status" to TicketStatus.OPEN.name,
                    "creatorUid" to uid,
                    "creatorName" to adminName,
                    "creatorEmail" to adminEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "tk_starter_02" to mapOf(
                    "ticketCode" to "TK-9002",
                    "title" to "Kiểm tra kết nối máy in bill POS quầy mang đi",
                    "description" to "Máy in nhiệt không nhận lệnh in từ phần mềm bán hàng sau khi đổi router Wi-Fi.",
                    "category" to "Mạng & Kết nối",
                    "priority" to TicketPriority.MEDIUM.name,
                    "status" to TicketStatus.OPEN.name,
                    "creatorUid" to uid,
                    "creatorName" to adminName,
                    "creatorEmail" to adminEmail,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            var productsCreated = 0
            var inventoryCreated = 0
            var ticketsCreated = 0

            val batch = db.batch()
            for ((docId, payload) in starterProducts) {
                val ref = db.collection("products").document(docId)
                if (!ref.get().await().exists()) {
                    batch.set(ref, payload)
                    productsCreated++
                }
            }
            for ((docId, payload) in starterInventory) {
                val ref = db.collection("inventory").document(docId)
                if (!ref.get().await().exists()) {
                    batch.set(ref, payload)
                    inventoryCreated++
                }
            }
            for ((docId, payload) in starterTickets) {
                val ref = db.collection("tickets").document(docId)
                if (!ref.get().await().exists()) {
                    batch.set(ref, payload)
                    ticketsCreated++
                }
            }

            if (productsCreated + inventoryCreated + ticketsCreated > 0) {
                batch.commit().await()
            }

            Result.success(
                SeedSummary(
                    inventoryCreated = inventoryCreated,
                    productsCreated = productsCreated,
                    ticketsCreated = ticketsCreated
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "inventory")
            Result.failure(e)
        }
    }
}
