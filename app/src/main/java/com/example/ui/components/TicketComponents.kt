package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.SupportTicket
import com.example.data.TicketPriority
import com.example.data.TicketStatus
import com.example.data.UserRole
import com.example.ui.theme.LocalSemanticColors
import com.example.util.PhotoUtils
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val ticketDateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))

fun formatTimestampVi(ts: Timestamp?): String {
    val date = (ts ?: Timestamp.now()).toDate()
    return ticketDateFormatter.format(date)
}

@Composable
fun statusColors(status: TicketStatus): Pair<Color, Color> {
    val palette = LocalSemanticColors.current
    return when (status) {
        TicketStatus.OPEN -> palette.openFg to palette.openBg
        TicketStatus.ACCEPTED -> palette.acceptedFg to palette.acceptedBg
        TicketStatus.IN_PROGRESS -> palette.progressFg to palette.progressBg
        TicketStatus.COMPLETED -> palette.completedFg to palette.completedBg
    }
}

@Composable
fun priorityColors(priority: TicketPriority): Pair<Color, Color> {
    val palette = LocalSemanticColors.current
    return when (priority) {
        TicketPriority.LOW -> palette.lowFg to palette.lowBg
        TicketPriority.MEDIUM -> palette.mediumFg to palette.mediumBg
        TicketPriority.HIGH -> palette.highFg to palette.highBg
        TicketPriority.URGENT -> palette.urgentFg to palette.urgentBg
    }
}

@Composable
fun StatusBadge(status: TicketStatus, modifier: Modifier = Modifier) {
    val (fg, bg) = statusColors(status)
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = status.labelVi,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PriorityBadge(priority: TicketPriority, modifier: Modifier = Modifier) {
    val (fg, bg) = priorityColors(priority)
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Flag,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = priority.labelVi,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}

@Composable
fun RoleBadge(role: UserRole, modifier: Modifier = Modifier) {
    val container = when (role) {
        UserRole.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
        UserRole.ADMIN -> MaterialTheme.colorScheme.errorContainer
        UserRole.TEAM_LEAD -> MaterialTheme.colorScheme.secondary
        UserRole.TECHNICIAN -> MaterialTheme.colorScheme.primaryContainer
        UserRole.USER -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (role) {
        UserRole.PENDING -> MaterialTheme.colorScheme.onTertiaryContainer
        UserRole.ADMIN -> MaterialTheme.colorScheme.onErrorContainer
        UserRole.TEAM_LEAD -> MaterialTheme.colorScheme.onSecondary
        UserRole.TECHNICIAN -> MaterialTheme.colorScheme.onPrimaryContainer
        UserRole.USER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = role.labelVi,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TicketSummaryCard(
    ticket: SupportTicket,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusFg, _) = statusColors(ticket.parsedStatus)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ticket_card_${ticket.id}")
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(statusFg)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${ticket.ticketCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PriorityBadge(priority = ticket.parsedPriority)
                    }
                    StatusBadge(status = ticket.parsedStatus)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = ticket.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ticket.assigneeName?.let { "KTV: $it" } ?: "Chưa phân công",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!ticket.defectPhotoBase64.isNullOrBlank() || !ticket.resolvedPhotoBase64.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "Có ảnh đính kèm",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimestampVi(ticket.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusProgressStepper(currentStatus: TicketStatus, modifier: Modifier = Modifier) {
    val steps = TicketStatus.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val reached = index <= currentStatus.stepIndex
            val (fg, _) = statusColors(currentStatus)
            val dotColor = if (reached) fg else MaterialTheme.colorScheme.outlineVariant

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (index == 0) Color.Transparent
                                else if (reached) fg else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (reached) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (fg.luminance() > 0.45f) Color(0xFF181615) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                if (index == steps.lastIndex) Color.Transparent
                                else if (index < currentStatus.stepIndex) fg else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = step.labelVi,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (step == currentStatus) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun Base64PhotoPreview(
    base64: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    allowFullscreen: Boolean = true
) {
    var bitmap by remember(base64) { mutableStateOf<ImageBitmap?>(null) }
    var showFull by remember { mutableStateOf(false) }

    LaunchedEffect(base64) {
        bitmap = PhotoUtils.decodeBase64ToImageBitmap(base64)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (allowFullscreen && bitmap != null) {
                    Modifier.clickable { showFull = true }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }

    if (showFull && bitmap != null) {
        Dialog(
            onDismissRequest = { showFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { showFull = false }
            ) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                IconButton(
                    onClick = { showFull = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Đóng ảnh",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun createTempPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ticket_photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun PhotoCapturePickerCard(
    title: String,
    subtitle: String,
    photoBase64: String?,
    onPhotoReady: (String?) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "photo"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCompressing by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val handleUri: (Uri) -> Unit = { uri ->
        scope.launch {
            isCompressing = true
            photoError = null
            PhotoUtils.compressUriToBase64(context, uri)
                .onSuccess { encoded -> onPhotoReady(encoded) }
                .onFailure { err -> photoError = err.localizedMessage ?: "Không thể xử lý ảnh." }
            isCompressing = false
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            handleUri(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempPhotoUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            photoError = "Cần quyền Camera để chụp ảnh trực tiếp. Bạn vẫn có thể chọn ảnh từ thư viện."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) handleUri(uri)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isCompressing) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Đang nén và chuẩn bị ảnh...", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!photoBase64.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Base64PhotoPreview(
                        base64 = photoBase64,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        IconButton(
                            onClick = { onPhotoReady(null) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Xóa ảnh",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (photoError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = photoError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoError = null
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            val uri = createTempPhotoUri(context)
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !isCompressing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("${testTagPrefix}_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chụp ảnh", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        photoError = null
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isCompressing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("${testTagPrefix}_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chọn ảnh", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
