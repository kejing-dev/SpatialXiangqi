package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/** Icon button with a caption underneath, used in the toolbar. */
@Composable
fun ToolItem(iconRes: Int, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val tint = when {
            !enabled -> PicoTheme.colorScheme.labelQuaternary
            active -> PicoTheme.colorScheme.interaction
            else -> PicoTheme.colorScheme.labelPrimary
        }
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = tint),
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = label, modifier = Modifier.size(28.dp), tint = tint)
        }
        Text(
            label,
            style = PicoTheme.typography.bodyTiny,
            color = if (enabled) PicoTheme.colorScheme.labelSecondary else PicoTheme.colorScheme.labelQuaternary,
        )
    }
}

/** Icon button without the default dark disc: dark glyph directly on the glass (device feedback 2026-09-24). */
@Composable
fun FlatIconButton(iconRes: Int, contentDescription: String, enabled: Boolean = true, size: Dp = 26.dp, onClick: () -> Unit) {
    val tint = if (enabled) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelQuaternary
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = tint),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(size), tint = tint)
    }
}

/** Thin vertical divider between toolbar groups. */
@Composable
fun ToolDivider() {
    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(44.dp).background(PicoTheme.colorScheme.dividerLine))
}

/** Text-only segmented control bound to an index. SegmentControl is greedy, so it gets a width by default. */
@Composable
fun TextSegments(options: List<String>, selected: Int, modifier: Modifier? = null, onSelect: (Int) -> Unit) {
    val longest = options.maxOf { it.length }
    val itemWidth = (if (longest > 4) 120 else if (longest > 2) 96 else 80).dp
    SegmentControl(modifier = modifier ?: Modifier.width(itemWidth * options.size + 8.dp)) {
        options.forEachIndexed { i, label ->
            SegmentItem(
                selected = i == selected,
                onClick = { onSelect(i) },
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Text(label, style = PicoTheme.typography.labelMedium)
                },
            )
        }
    }
}

/** Section heading inside sheets. */
@Composable
fun SectionTitle(text: String) {
    Text(text, style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

/** Label on the left, control on the right. */
@Composable
fun SettingRow(label: String, supporting: String? = null, control: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = PicoTheme.typography.bodyLarge, color = PicoTheme.colorScheme.labelPrimary)
            if (supporting != null) Text(supporting, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
        }
        control()
    }
}

/** Label above the control: for narrow sheet columns where a segmented control needs the full width. */
@Composable
fun SettingBlock(label: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        control()
    }
}

/** Click handling for SpatialUI list/nav items that already draw their own hover: indication + shared haptics. */
@Composable
fun Modifier.spatialClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .clickable(enabled = enabled, interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
        .controllerHapticFeedback(interactionSource = interactionSource)
}

/** Custom clickable row following the design-style hover + haptics contract. */
@Composable
fun ClickableRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    selectedColor: Color = PicoTheme.colorScheme.fillSecondary,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .spatialHoverEffect()
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .controllerHapticFeedback(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
