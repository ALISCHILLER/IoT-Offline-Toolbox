package com.msa.iotofflinetoolbox.ui.components

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.ui.layout.responsiveLayout
import com.msa.iotofflinetoolbox.ui.theme.toolboxUiTokens


val LocalCompactMode = staticCompositionLocalOf { false }

@Composable
fun responsiveContentPadding(): PaddingValues {
    val layout = responsiveLayout()
    val compact = LocalCompactMode.current
    val horizontal = if (compact) (layout.horizontalPadding - 4.dp).coerceAtLeast(8.dp) else layout.horizontalPadding
    val vertical = if (compact) (layout.verticalPadding - 4.dp).coerceAtLeast(6.dp) else layout.verticalPadding
    return PaddingValues(
        start = horizontal,
        top = vertical,
        end = horizontal,
        bottom = vertical + if (compact) 8.dp else 16.dp,
    )
}

@Composable
fun responsivePageSpacing(): Dp = if (LocalCompactMode.current) 10.dp else responsiveLayout().pageSpacing

@Composable
fun responsiveCardPadding(): Dp = if (LocalCompactMode.current) 12.dp else responsiveLayout().cardPadding

@Composable
fun responsiveTabEdgePadding(): Dp = if (responsiveLayout().isLowHeight) 6.dp else 12.dp

@Composable
fun responsiveTextAreaMinLines(normal: Int, lowHeight: Int = 2): Int =
    if (responsiveLayout().isLowHeight) lowHeight else normal

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    eyebrow: String? = null,
) {
    val layout = responsiveLayout()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 4.dp else 7.dp),
    ) {
        eyebrow?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            title,
            style = when {
                layout.isLowHeight -> MaterialTheme.typography.titleLarge
                layout.isCompactWidth -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.headlineMedium
            },
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            subtitle,
            style = if (layout.isLowHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PageHeaderWithAction(
    title: String,
    subtitle: String,
    eyebrow: String? = null,
    action: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val layout = responsiveLayout()
        if (maxWidth < 600.dp || (layout.isLowHeight && maxWidth < 800.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeader(title, subtitle, eyebrow)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.weight(1f)) { PageHeader(title, subtitle, eyebrow) }
                action()
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    MetricCard(
        label = title,
        value = value,
        supporting = supporting,
        modifier = modifier,
    )
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val layout = responsiveLayout()
    ElevatedCard(
        modifier = modifier.heightIn(min = if (layout.isLowHeight) 104.dp else 124.dp),
        shape = RoundedCornerShape(layout.cardCornerRadius),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 5.dp else 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                icon?.let {
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                        )
                    }
                }
            }
            Text(
                value,
                style = if (layout.isLowHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = responsiveLayout()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        val compact = LocalCompactMode.current
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else layout.cardSpacing),
        ) {
            if (action == null) {
                SectionTitle(title)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 440.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(title)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.weight(1f)) { SectionTitle(title) }
                            action()
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    val layout = responsiveLayout()
    Text(
        title,
        style = if (layout.isLowHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun ToolActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val layout = responsiveLayout()
    val container = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ElevatedCard(
        modifier = modifier
            .heightIn(min = if (layout.isLowHeight) 112.dp else 138.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(layout.cardCornerRadius),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (emphasized) 2.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 8.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (emphasized) contentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (emphasized) contentColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(24.dp),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.72f),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = if (layout.isLowHeight) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Immutable
enum class StatusTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL,
}

@Composable
fun StatusBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.INFO,
    action: (@Composable () -> Unit)? = null,
) {
    val tokens = toolboxUiTokens
    val (icon, container, content) = when (tone) {
        StatusTone.INFO -> Triple(Icons.Outlined.Info, tokens.infoContainer, tokens.onInfoContainer)
        StatusTone.SUCCESS -> Triple(Icons.Outlined.CheckCircle, tokens.successContainer, tokens.onSuccessContainer)
        StatusTone.WARNING -> Triple(Icons.Outlined.WarningAmber, tokens.warningContainer, tokens.onWarningContainer)
        StatusTone.ERROR -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        StatusTone.NEUTRAL -> Triple(Icons.Outlined.Info, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactAction = action != null && maxWidth < 520.dp
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactAction) 10.dp else 0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(message, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.84f))
                    }
                    if (!compactAction) action?.invoke()
                }
                if (compactAction) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        action?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
fun InfoPill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(99.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CapabilityBadge(label: String, enabled: Boolean) {
    val layout = responsiveLayout()
    val tokens = toolboxUiTokens
    val container = if (enabled) tokens.successContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (enabled) tokens.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val text = if (enabled) stringResource(Res.string.available_98bb09d7) else stringResource(Res.string.limited_3d89e19f)
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(container)
            .border(1.dp, content.copy(alpha = 0.12f), RoundedCornerShape(99.dp))
            .padding(
                horizontal = if (layout.isLowHeight) 10.dp else 12.dp,
                vertical = if (layout.isLowHeight) 6.dp else 7.dp,
            )
            .semantics(mergeDescendants = true) { stateDescription = text },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(if (enabled) tokens.success else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text("$label · $text", style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospaced: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.SansSerif,
                        ),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    label,
                    modifier = Modifier.width(132.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.SansSerif,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun CodeSurface(
    text: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 360.dp,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal)
                    .padding(14.dp),
            ) {
                Text(
                    text = text.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}


@Composable
fun ToolTabRow(
    selectedIndex: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        edgePadding = responsiveTabEdgePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        },
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ResponsiveFields(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 480.dp,
    vararg fields: @Composable (Modifier) -> Unit,
) {
    if (fields.isEmpty()) return
    val layout = responsiveLayout()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = if (LocalCompactMode.current || layout.isLowHeight) 8.dp else 12.dp
        val widthColumns = ((maxWidth.value + spacing.value) /
            (layout.minimumFieldWidth.value + spacing.value)).toInt().coerceAtLeast(1)
        val columns = when {
            maxWidth < breakpoint -> 1
            else -> widthColumns.coerceAtMost(fields.size)
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            fields.toList().chunked(columns).forEach { rowFields ->
                if (rowFields.size == 1) {
                    rowFields.first()(Modifier.fillMaxWidth())
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowFields.forEach { field -> field(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResponsiveActions(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    val layout = responsiveLayout()
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (LocalCompactMode.current || layout.isLowHeight) 7.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (LocalCompactMode.current || layout.isLowHeight) 7.dp else 9.dp),
        maxItemsInEachRow = Int.MAX_VALUE,
        content = content,
    )
}

@Composable
fun ResponsiveButtonGrid(
    modifier: Modifier = Modifier,
    minimumButtonWidth: Dp = 210.dp,
    vararg buttons: @Composable (Modifier) -> Unit,
) {
    if (buttons.isEmpty()) return
    val layout = responsiveLayout()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = if (layout.isLowHeight) 8.dp else 10.dp
        val columns = ((maxWidth.value + spacing.value) /
            (minimumButtonWidth.value + spacing.value)).toInt().coerceIn(1, buttons.size)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            buttons.toList().chunked(columns).forEach { rowButtons ->
                if (rowButtons.size == 1) {
                    rowButtons.first()(Modifier.fillMaxWidth())
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowButtons.forEach { button -> button(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ResponsiveCardGrid(
    modifier: Modifier = Modifier,
    minimumCardWidth: Dp = 360.dp,
    vararg cards: @Composable (Modifier) -> Unit,
) {
    ResponsiveCardGrid(
        cards = cards.toList(),
        modifier = modifier,
        minimumCardWidth = minimumCardWidth,
    )
}

@Composable
fun ResponsiveCardGrid(
    cards: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier,
    minimumCardWidth: Dp = 360.dp,
) {
    if (cards.isEmpty()) return
    val layout = responsiveLayout()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = layout.pageSpacing
        val columns = ((maxWidth.value + spacing.value) /
            (minimumCardWidth.value + spacing.value)).toInt().coerceIn(1, cards.size)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            cards.chunked(columns).forEach { rowCards ->
                if (rowCards.size == 1) {
                    rowCards.first()(Modifier.fillMaxWidth())
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowCards.forEach { card -> card(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ResponsivePanes(
    modifier: Modifier = Modifier,
    minimumPaneWidth: Dp = 400.dp,
    primary: @Composable (Modifier) -> Unit,
    secondary: @Composable (Modifier) -> Unit,
) {
    val layout = responsiveLayout()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = layout.pageSpacing
        val canSplit = maxWidth >= minimumPaneWidth * 2 + spacing
        if (canSplit) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top,
            ) {
                primary(Modifier.weight(1f))
                secondary(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun LogLevelBadge(level: LogLevel) {
    val tokens = toolboxUiTokens
    val (icon, container, content) = when (level) {
        LogLevel.INFO -> Triple(Icons.Outlined.Info, tokens.infoContainer, tokens.onInfoContainer)
        LogLevel.SUCCESS -> Triple(Icons.Outlined.CheckCircle, tokens.successContainer, tokens.onSuccessContainer)
        LogLevel.WARNING -> Triple(Icons.Outlined.WarningAmber, tokens.warningContainer, tokens.onWarningContainer)
        LogLevel.ERROR -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(11.dp)) {
        Icon(icon, contentDescription = level.name, modifier = Modifier.padding(7.dp).size(18.dp))
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Outlined.Info,
    action: (@Composable () -> Unit)? = null,
) {
    val layout = responsiveLayout()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (layout.isLowHeight) 22.dp else 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(16.dp).size(28.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.82f),
        )
        action?.invoke()
    }
}
