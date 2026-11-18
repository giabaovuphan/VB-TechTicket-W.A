package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.InvoiceLineItem
import com.example.data.InvoicePaymentMethod
import com.example.data.InvoicePaymentStatus
import com.example.data.ProductItem
import com.example.data.ProductUnits
import com.example.data.RepairInvoice
import com.example.data.StockAlertLevel
import com.example.data.SupportTicket
import com.example.data.formatVnd

private enum class InvoicePickerSource {
    INVENTORY,
    PRODUCTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditorSheet(
    ticket: SupportTicket,
    existingInvoice: RepairInvoice?,
    activeProducts: List<ProductItem>,
    activeInventory: List<InventoryItem>,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        items: List<InvoiceLineItem>,
        laborFee: Long,
        paymentStatus: InvoicePaymentStatus,
        paymentMethod: InvoicePaymentMethod,
        note: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lineItems = remember(existingInvoice?.id) {
        mutableStateListOf<InvoiceLineItem>().apply {
            if (existingInvoice != null) addAll(existingInvoice.items)
        }
    }

    var pickerSource by remember(activeInventory.isEmpty(), activeProducts.isEmpty()) {
        mutableStateOf(
            if (activeInventory.isNotEmpty() || activeProducts.isEmpty()) {
                InvoicePickerSource.INVENTORY
            } else {
                InvoicePickerSource.PRODUCTS
            }
        )
    }
    var catalogSearch by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualUnit by remember { mutableStateOf(ProductUnits.first()) }
    var manualQtyText by remember { mutableStateOf("1") }
    var manualPriceText by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }

    var laborFeeText by remember(existingInvoice?.id) {
        mutableStateOf(existingInvoice?.laborFee?.takeIf { it > 0L }?.toString() ?: "")
    }
    var paymentMethod by remember(existingInvoice?.id) {
        mutableStateOf(existingInvoice?.parsedPaymentMethod ?: InvoicePaymentMethod.CASH)
    }
    var paymentStatus by remember(existingInvoice?.id) {
        mutableStateOf(existingInvoice?.parsedPaymentStatus ?: InvoicePaymentStatus.UNPAID)
    }
    var note by remember(existingInvoice?.id) {
        mutableStateOf(existingInvoice?.note ?: "")
    }
    var formError by remember { mutableStateOf<String?>(null) }

    val inventoryById = remember(activeInventory) { activeInventory.associateBy { it.id } }
    val existingQtyByInventoryId = remember(existingInvoice?.id) {
        val map = mutableMapOf<String, Int>()
        existingInvoice?.items?.forEach { line ->
            val invId = line.inventoryId?.takeIf { it.isNotBlank() }
            if (invId != null) {
                map[invId] = (map[invId] ?: 0) + line.quantity
            }
        }
        map
    }

    val partsSubtotal = lineItems.sumOf { it.unitPrice * it.quantity }
    val laborFee = laborFeeText.toLongOrNull() ?: 0L
    val totalAmount = partsSubtotal + laborFee

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingInvoice == null) {
                    "Tạo hóa đơn sửa chữa #${ticket.ticketCode}"
                } else {
                    "Cập nhật hóa đơn ${existingInvoice.invoiceCode}"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. Pick from inventory (auto stock deduction) or shared product catalog
            Text(
                text = "Chọn linh kiện xuất hóa đơn",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = pickerSource == InvoicePickerSource.INVENTORY,
                    onClick = { pickerSource = InvoicePickerSource.INVENTORY },
                    label = { Text("Từ Kho tồn (${activeInventory.size})") },
                    modifier = Modifier.testTag("invoice_source_inventory")
                )
                FilterChip(
                    selected = pickerSource == InvoicePickerSource.PRODUCTS,
                    onClick = { pickerSource = InvoicePickerSource.PRODUCTS },
                    label = { Text("Bảng giá (${activeProducts.size})") },
                    modifier = Modifier.testTag("invoice_source_products")
                )
            }

            OutlinedTextField(
                value = catalogSearch,
                onValueChange = { catalogSearch = it },
                placeholder = { Text("Tìm linh kiện hoặc mã SKU...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (catalogSearch.isNotEmpty()) {
                        IconButton(onClick = { catalogSearch = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Xóa từ khóa")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (pickerSource == InvoicePickerSource.INVENTORY) {
                val filteredInventory = remember(activeInventory, catalogSearch) {
                    val q = catalogSearch.trim().lowercase()
                    activeInventory.filter { inv ->
                        q.isBlank() ||
                            inv.name.lowercase().contains(q) ||
                            (inv.sku?.lowercase()?.contains(q) == true) ||
                            inv.category.lowercase().contains(q)
                    }
                }

                if (filteredInventory.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activeInventory.isEmpty()) {
                                "Kho tồn chưa có vật tư đang hoạt động. Bạn có thể chuyển sang tab Bảng giá hoặc nhập tay bên dưới."
                            } else {
                                "Không tìm thấy vật tư trong kho khớp từ khóa \"$catalogSearch\"."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredInventory.forEach { inv ->
                            val currentInDraft = lineItems.filter { it.inventoryId == inv.id }.sumOf { it.quantity }
                            val maxAllowed = inv.quantityOnHand.toInt() + (existingQtyByInventoryId[inv.id] ?: 0)
                            val remaining = (maxAllowed - currentInDraft).coerceAtLeast(0)
                            val isOutOfStock = remaining <= 0

                            Card(
                                onClick = {
                                    if (isOutOfStock) {
                                        formError = "Vật tư \"${inv.name}\" đã hết tồn kho khả dụng để xuất thêm."
                                    } else {
                                        formError = null
                                        val existingIdx = lineItems.indexOfFirst { it.inventoryId == inv.id }
                                        if (existingIdx >= 0) {
                                            val curr = lineItems[existingIdx]
                                            val nextQty = curr.quantity + 1
                                            lineItems[existingIdx] = curr.copy(
                                                quantity = nextQty,
                                                lineTotal = curr.unitPrice * nextQty
                                            )
                                        } else if (lineItems.size < 50) {
                                            lineItems.add(
                                                InvoiceLineItem(
                                                    productId = inv.productId,
                                                    inventoryId = inv.id,
                                                    name = inv.name,
                                                    unit = inv.unit,
                                                    quantity = 1,
                                                    unitPrice = inv.unitPrice,
                                                    lineTotal = inv.unitPrice,
                                                    customEntry = false
                                                )
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOutOfStock) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    }
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isOutOfStock) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.width(205.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = inv.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatVnd(inv.unitPrice)} / ${inv.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when {
                                            isOutOfStock -> "Hết hàng khả dụng"
                                            inv.alertLevel == StockAlertLevel.LOW -> "Sắp hết • Còn ${inv.quantityOnHand} ${inv.unit}"
                                            else -> "Còn ${inv.quantityOnHand} ${inv.unit} • Trừ kho"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOutOfStock) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val filteredProducts = remember(activeProducts, catalogSearch) {
                    val q = catalogSearch.trim().lowercase()
                    activeProducts.filter { p ->
                        q.isBlank() ||
                            p.name.lowercase().contains(q) ||
                            (p.sku?.lowercase()?.contains(q) == true) ||
                            p.category.lowercase().contains(q)
                    }
                }

                if (filteredProducts.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activeProducts.isEmpty()) {
                                "Chưa có linh kiện trong bảng giá chung. Bạn có thể thêm mục nhập tay bên dưới."
                            } else {
                                "Không tìm thấy linh kiện khớp từ khóa \"$catalogSearch\"."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredProducts.forEach { product ->
                            Card(
                                onClick = {
                                    formError = null
                                    val existingIdx = lineItems.indexOfFirst {
                                        it.inventoryId == null && it.productId == product.id
                                    }
                                    if (existingIdx >= 0) {
                                        val curr = lineItems[existingIdx]
                                        val nextQty = curr.quantity + 1
                                        lineItems[existingIdx] = curr.copy(
                                            quantity = nextQty,
                                            lineTotal = curr.unitPrice * nextQty
                                        )
                                    } else if (lineItems.size < 50) {
                                        lineItems.add(
                                            InvoiceLineItem(
                                                productId = product.id,
                                                inventoryId = null,
                                                name = product.name,
                                                unit = product.unit,
                                                quantity = 1,
                                                unitPrice = product.unitPrice,
                                                lineTotal = product.unitPrice,
                                                customEntry = false
                                            )
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.width(190.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatVnd(product.unitPrice)} / ${product.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.AddCircleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Chạm để thêm",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Manual entry toggle & form
            OutlinedButton(
                onClick = { showManualEntry = !showManualEntry },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("toggle_manual_item_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showManualEntry) "Ẩn khung nhập linh kiện ngoài" else "Thêm linh kiện / chi phí nhập tay")
            }

            if (showManualEntry) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it; manualError = null },
                            label = { Text("Tên linh kiện / hạng mục *") },
                            placeholder = { Text("VD: Keo tản nhiệt, Dây nguồn...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_item_name_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualQtyText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        manualQtyText = input
                                        manualError = null
                                    }
                                },
                                label = { Text("SL *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.35f)
                            )
                            OutlinedTextField(
                                value = manualPriceText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        manualPriceText = input
                                        manualError = null
                                    }
                                },
                                label = { Text("Đơn giá (VNĐ) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.65f)
                                    .testTag("manual_item_price_input")
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProductUnits.forEach { u ->
                                FilterChip(
                                    selected = manualUnit == u,
                                    onClick = { manualUnit = u },
                                    label = { Text(u) }
                                )
                            }
                        }
                        if (manualError != null) {
                            Text(
                                text = manualError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                val cleanName = manualName.trim()
                                val qty = manualQtyText.toIntOrNull()
                                val price = manualPriceText.toLongOrNull()
                                when {
                                    cleanName.isBlank() -> manualError = "Nhập tên linh kiện hoặc hạng mục."
                                    qty == null || qty < 1 -> manualError = "Số lượng phải từ 1 trở lên."
                                    price == null || price < 0L -> manualError = "Nhập đơn giá hợp lệ."
                                    lineItems.size >= 50 -> manualError = "Tối đa 50 mục trong một hóa đơn."
                                    else -> {
                                        lineItems.add(
                                            InvoiceLineItem(
                                                productId = null,
                                                name = cleanName,
                                                unit = manualUnit,
                                                quantity = qty,
                                                unitPrice = price,
                                                lineTotal = price * qty,
                                                customEntry = true
                                            )
                                        )
                                        manualName = ""
                                        manualQtyText = "1"
                                        manualPriceText = ""
                                        manualError = null
                                        formError = null
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_manual_item_confirm_button")
                        ) {
                            Text("Thêm vào hóa đơn")
                        }
                    }
                }
            }

            // 3. Current line items in invoice
            if (lineItems.isNotEmpty()) {
                Text(
                    text = "Danh sách đã chọn (${lineItems.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lineItems.forEachIndexed { index, item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${formatVnd(item.unitPrice)} / ${item.unit} = ${formatVnd(item.unitPrice * item.quantity)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!item.inventoryId.isNullOrBlank()) {
                                        Text(
                                            text = "Trừ kho tự động",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (item.quantity > 1) {
                                            val nextQty = item.quantity - 1
                                            lineItems[index] = item.copy(
                                                quantity = nextQty,
                                                lineTotal = item.unitPrice * nextQty
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Giảm số lượng", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = "${item.quantity}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = {
                                        val invId = item.inventoryId?.takeIf { it.isNotBlank() }
                                        val matchedInv = invId?.let { inventoryById[it] }
                                        if (matchedInv != null) {
                                            val maxAllowed = matchedInv.quantityOnHand.toInt() + (existingQtyByInventoryId[matchedInv.id] ?: 0)
                                            val totalDraftQty = lineItems.filter { it.inventoryId == matchedInv.id }.sumOf { it.quantity }
                                            if (totalDraftQty >= maxAllowed) {
                                                formError = "Vật tư \"${item.name}\" đã đạt tối đa tồn kho khả dụng ($maxAllowed ${item.unit})."
                                                return@IconButton
                                            }
                                        }
                                        formError = null
                                        val nextQty = item.quantity + 1
                                        lineItems[index] = item.copy(
                                            quantity = nextQty,
                                            lineTotal = item.unitPrice * nextQty
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Tăng số lượng", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { lineItems.removeAt(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Xóa dòng",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Labor fee & Payment details
            OutlinedTextField(
                value = laborFeeText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) {
                        laborFeeText = input
                        formError = null
                    }
                },
                label = { Text("Phí công sửa chữa / kiểm tra (VNĐ)") },
                placeholder = { Text("VD: 150000 (để trống nếu miễn phí)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("labor_fee_input")
            )

            Text("Phương thức thanh toán", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InvoicePaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.labelVi) }
                    )
                }
            }

            Text("Trạng thái thanh toán", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InvoicePaymentStatus.entries.forEach { st ->
                    FilterChip(
                        selected = paymentStatus == st,
                        onClick = { paymentStatus = st },
                        label = { Text(st.labelVi) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú hóa đơn / Bảo hành (tùy chọn)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Total Summary Card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tiền linh kiện:", style = MaterialTheme.typography.bodySmall)
                        Text(formatVnd(partsSubtotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Phí công sửa chữa:", style = MaterialTheme.typography.bodySmall)
                        Text(formatVnd(laborFee), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Tổng cộng:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatVnd(totalAmount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (formError != null) {
                Text(
                    text = formError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    if (lineItems.isEmpty() && laborFee <= 0L && paymentMethod != InvoicePaymentMethod.INTERNAL) {
                        formError = "Vui lòng thêm ít nhất 1 linh kiện hoặc nhập phí công sửa chữa."
                    } else {
                        onSave(
                            lineItems.toList(),
                            laborFee,
                            paymentStatus,
                            paymentMethod,
                            note.trim().ifBlank { null }
                        )
                    }
                },
                enabled = !isProcessing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_invoice_button")
            ) {
                Text(if (existingInvoice == null) "Lưu & Tạo hóa đơn" else "Cập nhật hóa đơn")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
