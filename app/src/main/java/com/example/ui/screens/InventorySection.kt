package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.example.data.ProductUnits
import com.example.data.StockAlertLevel
import com.example.data.TicketCategories
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.UserRole
import com.example.data.formatVnd
import com.example.ui.theme.LocalSemanticColors

fun LazyListScope.inventoryListSection(
    currentUser: UserAccount,
    inventoryState: UiState<List<InventoryItem>>,
    searchQuery: String,
    isSaving: Boolean,
    onEditItem: (InventoryItem) -> Unit,
    onDeleteItem: (InventoryItem) -> Unit,
    onAdjustStock: (InventoryItem, Long) -> Unit,
    onToggleActive: (InventoryItem, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onSeedStarterData: () -> Unit
) {
    when (inventoryState) {
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
                    text = inventoryState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is UiState.Success -> {
            val allItems = inventoryState.data
            val lowCount = allItems.count { it.alertLevel == StockAlertLevel.LOW }
            val outCount = allItems.count { it.alertLevel == StockAlertLevel.OUT }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InventoryKpiMiniCard(
                        label = "Tổng vật tư",
                        value = "${allItems.size}",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    InventoryKpiMiniCard(
                        label = "Sắp hết",
                        value = "$lowCount",
                        accent = LocalSemanticColors.current.highFg,
                        modifier = Modifier.weight(1f)
                    )
                    InventoryKpiMiniCard(
                        label = "Hết hàng",
                        value = "$outCount",
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val q = searchQuery.trim().lowercase()
            val filtered = allItems.filter { item ->
                q.isBlank() ||
                    item.name.lowercase().contains(q) ||
                    (item.sku?.lowercase()?.contains(q) == true) ||
                    item.category.lowercase().contains(q) ||
                    (item.location?.lowercase()?.contains(q) == true)
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
                                imageVector = Icons.Filled.Inventory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (allItems.isEmpty()) "Kho linh kiện chưa có dữ liệu" else "Không tìm thấy vật tư phù hợp",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Thêm linh kiện vào kho để theo dõi tồn kho, cảnh báo sắp hết và tự động trừ khi lập hóa đơn.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (allItems.isEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onAddClick,
                                        enabled = !isSaving,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("Nhập vật tư")
                                    }
                                    if (currentUser.parsedRole == UserRole.ADMIN) {
                                        Button(
                                            onClick = onSeedStarterData,
                                            enabled = !isSaving,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("empty_seed_starter_button")
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Dữ liệu mẫu")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    InventoryItemCard(
                        item = item,
                        isSaving = isSaving,
                        onAdjustStock = onAdjustStock,
                        onToggleActive = onToggleActive,
                        onEdit = { onEditItem(item) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryKpiMiniCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    isSaving: Boolean,
    onAdjustStock: (InventoryItem, Long) -> Unit,
    onToggleActive: (InventoryItem, Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalSemanticColors.current
    val (badgeFg, badgeBg) = when (item.alertLevel) {
        StockAlertLevel.OK -> palette.completedFg to palette.completedBg
        StockAlertLevel.LOW -> palette.highFg to palette.highBg
        StockAlertLevel.OUT -> palette.urgentFg to palette.urgentBg
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (item.alertLevel == StockAlertLevel.OK) 1.dp else 1.5.dp,
            color = if (item.alertLevel == StockAlertLevel.OK) MaterialTheme.colorScheme.outlineVariant else badgeFg.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(item.category)
                            if (!item.sku.isNullOrBlank()) append(" • SKU: ${item.sku}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (item.alertLevel != StockAlertLevel.OK) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "${item.alertLevel.labelVi}: ${item.quantityOnHand} ${item.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeFg
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatVnd(item.unitPrice)} / ${item.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ngưỡng cảnh báo: ≤ ${item.minThreshold} ${item.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!item.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick -1 / +1 stock adjustment
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onAdjustStock(item, -1L) },
                        enabled = !isSaving && item.quantityOnHand > 0L,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("inventory_dec_${item.id}")
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Giảm tồn kho", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${item.quantityOnHand}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    OutlinedButton(
                        onClick = { onAdjustStock(item, 1L) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("inventory_inc_${item.id}")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Nhập thêm kho", modifier = Modifier.size(16.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = item.active,
                        onCheckedChange = { onToggleActive(item, it) },
                        enabled = !isSaving
                    )
                    IconButton(
                        onClick = onEdit,
                        enabled = !isSaving,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Chỉnh sửa vật tư",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Xóa vật tư",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryEditorDialog(
    initial: InventoryItem?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
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
    ) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var sku by remember(initial) { mutableStateOf(initial?.sku ?: "") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: TicketCategories.first()) }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: ProductUnits.first()) }
    var priceText by remember(initial) { mutableStateOf(initial?.unitPrice?.toString() ?: "") }
    var qtyText by remember(initial) { mutableStateOf(initial?.quantityOnHand?.toString() ?: "5") }
    var thresholdText by remember(initial) { mutableStateOf(initial?.minThreshold?.toString() ?: "2") }
    var location by remember(initial) { mutableStateOf(initial?.location ?: "") }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var active by remember(initial) { mutableStateOf(initial?.active ?: true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Nhập vật tư vào kho" else "Cập nhật vật tư kho")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Tên linh kiện / vật tư *") },
                    placeholder = { Text("VD: Bơm áp suất máy pha cà phê") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_name_input")
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("Mã SKU (tùy chọn)") },
                    placeholder = { Text("VD: INV-BOM-EX5") },
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
                    label = { Text("Đơn giá xuất (VNĐ) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inventory_price_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                qtyText = input
                                errorText = null
                            }
                        },
                        label = { Text("Tồn kho *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inventory_qty_input")
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                thresholdText = input
                                errorText = null
                            }
                        },
                        label = { Text("Cảnh báo ≤ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Vị trí kệ / kho (tùy chọn)") },
                    placeholder = { Text("VD: Kệ A1 - Kho HCM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    label = { Text("Ghi chú (tùy chọn)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cho phép xuất cho hóa đơn", style = MaterialTheme.typography.bodyMedium)
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
                    val parsedQty = qtyText.toLongOrNull()
                    val parsedThreshold = thresholdText.toLongOrNull()
                    when {
                        cleanName.length < 2 -> errorText = "Tên vật tư phải có ít nhất 2 ký tự."
                        parsedPrice == null || parsedPrice < 0L -> errorText = "Vui lòng nhập đơn giá hợp lệ."
                        parsedQty == null || parsedQty < 0L -> errorText = "Số lượng tồn kho phải ≥ 0."
                        parsedThreshold == null || parsedThreshold < 0L -> errorText = "Ngưỡng cảnh báo phải ≥ 0."
                        else -> onConfirm(
                            cleanName,
                            sku.trim().ifBlank { null },
                            initial?.productId,
                            category,
                            unit,
                            parsedPrice,
                            parsedQty,
                            parsedThreshold,
                            location.trim().ifBlank { null },
                            note.trim().ifBlank { null },
                            active
                        )
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_save_inventory_button")
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
