package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.example.data.ProductItem
import com.example.data.ProductUnits
import com.example.data.TicketCategories
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.formatVnd
import com.example.ui.viewmodel.DetailActionFeedback

private enum class CatalogSection {
    INVENTORY,
    CATALOG
}

@Composable
fun ProductCatalogScreen(
    currentUser: UserAccount,
    inventoryState: UiState<List<InventoryItem>>,
    productsState: UiState<List<ProductItem>>,
    isSaving: Boolean,
    feedback: DetailActionFeedback?,
    onSaveInventory: (
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
    ) -> Unit,
    onAdjustInventoryStock: (InventoryItem, Long) -> Unit,
    onToggleInventoryActive: (InventoryItem, Boolean) -> Unit,
    onDeleteInventory: (InventoryItem) -> Unit,
    onSeedStarterData: () -> Unit,
    onSaveProduct: (
        existing: ProductItem?,
        name: String,
        sku: String?,
        category: String,
        unit: String,
        unitPrice: Long,
        note: String?,
        active: Boolean
    ) -> Unit,
    onToggleActive: (ProductItem, Boolean) -> Unit,
    onDeleteProduct: (ProductItem) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(CatalogSection.INVENTORY) }
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var deletingProduct by remember { mutableStateOf<ProductItem?>(null) }

    var editingInventory by remember { mutableStateOf<InventoryItem?>(null) }
    var showInventoryDialog by remember { mutableStateOf(false) }
    var deletingInventory by remember { mutableStateOf<InventoryItem?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedSection == CatalogSection.INVENTORY) {
                            "Kho linh kiện & Tồn kho"
                        } else {
                            "Bảng giá linh kiện & Dịch vụ"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (selectedSection == CatalogSection.INVENTORY) {
                            "Theo dõi số lượng tồn, cảnh báo hết hàng và trừ kho theo hóa đơn."
                        } else {
                            "Quản lý bảng giá linh kiện dùng chung khi lập hóa đơn sửa chữa."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onClearFeedback()
                        if (selectedSection == CatalogSection.INVENTORY) {
                            editingInventory = null
                            showInventoryDialog = true
                        } else {
                            editingProduct = null
                            showProductDialog = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_product_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm mới")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == CatalogSection.INVENTORY,
                    onClick = { selectedSection = CatalogSection.INVENTORY },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Kho tồn (inventory)") },
                    modifier = Modifier.testTag("section_inventory_chip")
                )
                FilterChip(
                    selected = selectedSection == CatalogSection.CATALOG,
                    onClick = { selectedSection = CatalogSection.CATALOG },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PriceChange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Bảng giá (products)") },
                    modifier = Modifier.testTag("section_catalog_chip")
                )
            }
        }

        if (feedback != null) {
            item {
                Surface(
                    color = if (feedback.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = feedback.message,
                        color = if (feedback.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (selectedSection == CatalogSection.INVENTORY) {
                            "Tìm tên vật tư, mã SKU, vị trí kệ..."
                        } else {
                            "Tìm tên linh kiện, mã SKU, danh mục..."
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Xóa tìm kiếm")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_search_input")
            )
        }

        if (selectedSection == CatalogSection.INVENTORY) {
            inventoryListSection(
                currentUser = currentUser,
                inventoryState = inventoryState,
                searchQuery = searchQuery,
                isSaving = isSaving,
                onEditItem = { item ->
                    onClearFeedback()
                    editingInventory = item
                    showInventoryDialog = true
                },
                onDeleteItem = { item ->
                    onClearFeedback()
                    deletingInventory = item
                },
                onAdjustStock = onAdjustInventoryStock,
                onToggleActive = onToggleInventoryActive,
                onAddClick = {
                    onClearFeedback()
                    editingInventory = null
                    showInventoryDialog = true
                },
                onSeedStarterData = onSeedStarterData
            )
        } else when (productsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(
                        text = productsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is UiState.Success -> {
                val q = searchQuery.trim().lowercase()
                val filtered = productsState.data.filter { item ->
                    q.isBlank() ||
                        item.name.lowercase().contains(q) ||
                        (item.sku?.lowercase()?.contains(q) == true) ||
                        item.category.lowercase().contains(q)
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (productsState.data.isEmpty()) "Kho danh mục chưa có sản phẩm" else "Không tìm thấy linh kiện phù hợp",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Nhấn \"+ Thêm mới\" để lưu linh kiện, vật tư hoặc gói dịch vụ sửa chữa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { product ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_card_${product.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = buildString {
                                                append(product.category)
                                                if (!product.sku.isNullOrBlank()) append(" • SKU: ${product.sku}")
                                                append(" • ĐVT: ${product.unit}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatVnd(product.unitPrice),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (!product.note.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = product.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = product.active,
                                            onCheckedChange = { checked -> onToggleActive(product, checked) },
                                            enabled = !isSaving
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (product.active) "Đang áp dụng" else "Tạm ẩn",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (product.active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                onClearFeedback()
                                                editingProduct = product
                                                showProductDialog = true
                                            },
                                            enabled = !isSaving,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Chỉnh sửa linh kiện",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onClearFeedback()
                                                deletingProduct = product
                                            },
                                            enabled = !isSaving,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Xóa linh kiện",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInventoryDialog) {
        InventoryEditorDialog(
            initial = editingInventory,
            isSaving = isSaving,
            onDismiss = { showInventoryDialog = false },
            onConfirm = { name, sku, productId, category, unit, price, qty, threshold, location, note, active ->
                onSaveInventory(
                    editingInventory,
                    name,
                    sku,
                    productId,
                    category,
                    unit,
                    price,
                    qty,
                    threshold,
                    location,
                    note,
                    active
                )
                showInventoryDialog = false
            }
        )
    }

    val targetDeleteInventory = deletingInventory
    if (targetDeleteInventory != null) {
        AlertDialog(
            onDismissRequest = { deletingInventory = null },
            title = { Text("Xóa vật tư khỏi kho?") },
            text = {
                Text("Bạn có chắc muốn xóa \"${targetDeleteInventory.name}\" khỏi collection inventory? Các hóa đơn đã lập trước đó vẫn được giữ nguyên.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteInventory(targetDeleteInventory)
                        deletingInventory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingInventory = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (showProductDialog) {
        ProductEditorDialog(
            initial = editingProduct,
            isSaving = isSaving,
            onDismiss = { showProductDialog = false },
            onConfirm = { name, sku, category, unit, price, note, active ->
                onSaveProduct(editingProduct, name, sku, category, unit, price, note, active)
                showProductDialog = false
            }
        )
    }

    val targetDelete = deletingProduct
    if (targetDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Xóa sản phẩm / linh kiện?") },
            text = {
                Text("Bạn có chắc muốn xóa \"${targetDelete.name}\" khỏi danh mục dùng chung? Các hóa đơn đã lập trước đó vẫn được giữ nguyên.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(targetDelete)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    initial: ProductItem?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        sku: String?,
        category: String,
        unit: String,
        unitPrice: Long,
        note: String?,
        active: Boolean
    ) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var sku by remember(initial) { mutableStateOf(initial?.sku ?: "") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: TicketCategories.first()) }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: ProductUnits.first()) }
    var priceText by remember(initial) { mutableStateOf(initial?.unitPrice?.toString() ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var active by remember(initial) { mutableStateOf(initial?.active ?: true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Thêm linh kiện / sản phẩm" else "Cập nhật linh kiện")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Tên linh kiện / dịch vụ *") },
                    placeholder = { Text("VD: SSD NVMe 512GB Kingston") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("Mã SKU (tùy chọn)") },
                    placeholder = { Text("VD: LK-SSD-512") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            priceText = input
                            errorText = null
                        }
                    },
                    label = { Text("Đơn giá (VNĐ) *") },
                    placeholder = { Text("VD: 1250000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_price_input")
                )
                Text("Danh mục", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TicketCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Text("Đơn vị tính", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductUnits.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú / Bảo hành (tùy chọn)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cho phép chọn trên hóa đơn", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val parsedPrice = priceText.toLongOrNull()
                    when {
                        cleanName.length < 2 -> errorText = "Tên linh kiện phải có ít nhất 2 ký tự."
                        parsedPrice == null || parsedPrice < 0L -> errorText = "Vui lòng nhập đơn giá hợp lệ."
                        else -> onConfirm(
                            cleanName,
                            sku.trim().ifBlank { null },
                            category,
                            unit,
                            parsedPrice,
                            note.trim().ifBlank { null },
                            active
                        )
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_save_product_button")
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
