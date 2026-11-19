package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AiOperationMode
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWafaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val wafa = LocalWafaColors.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        color = wafa.glassSurface,
        border = BorderStroke(1.dp, wafa.glassBorder),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    val wafa = LocalWafaColors.current
    val (bg, fg) = when (priority) {
        "Urgent" -> wafa.dangerContainer to wafa.danger
        "High" -> wafa.warningContainer to wafa.warning
        "Low" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> wafa.accentBlueContainer to wafa.accentBlue
    }
    StatusPill(text = priority, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun OperationModePill(
    mode: AiOperationMode,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wafa = LocalWafaColors.current
    val label = if (language == AppLanguage.BN) mode.labelBn else mode.labelEn
    val (bg, fg) = when (mode) {
        AiOperationMode.COPILOT -> wafa.accentBlueContainer to wafa.accentBlue
        AiOperationMode.APPROVAL -> wafa.warningContainer to wafa.warning
        AiOperationMode.SMART_AUTOMATION -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.VerifiedUser, contentDescription = label, tint = fg, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    GlassCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WafaSubScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("top_bar_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun EmptyWorkspaceCard(
    title: String,
    description: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    showIllustration: Boolean = true,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showIllustration) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_inbox),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("empty_primary_cta")
                ) {
                    Text(primaryActionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("empty_secondary_cta")
                    ) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_cancel_button")) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SenderAvatar(name: String, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "Never"
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return fmt.format(Date(ms))
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
