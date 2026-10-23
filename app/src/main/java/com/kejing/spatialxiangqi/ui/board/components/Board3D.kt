package com.kejing.spatialxiangqi.ui.board.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.board.Phase
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.kejing.spatialxiangqi.ui.theme.XiangqiColors
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

/**
 * The volumetric content: the 3D board scene plus a small status plaque attached above it.
 * Taps are resolved to squares by entity identity and forwarded as [BoardEvent.TapSquare].
 */
@Composable
fun Board3D(state: BoardUiState, scene: BoardScene, tuningStamp: Int, onEvent: (BoardEvent) -> Unit) {
    val context = LocalContext.current
    var content by remember { mutableStateOf<SpatialViewContent?>(null) }
    LaunchedEffect(state, content, tuningStamp) {
        if (content != null) runCatching { scene.requestSync(state, tuningStamp) }.onFailure { Log.e(BoardScene.TAG, "sync failed", it) }
    }
    SpatialView(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectSpatialTapGesture(context, targetedToEntity = TargetEntity.any()) { value ->
                val square = scene.squareFor(value.targetEntity)
                Log.i(BoardScene.TAG, "tap entity=${value.targetEntity?.getName()} square=$square")
                if (square != null) onEvent(BoardEvent.TapSquare(square))
            }
        },
        attachments = {
            AttachmentPanel(id = "status") { StatusPlaque(state) }
        },
        initial = { spatialContent, attachments ->
            scene.attach(spatialContent)
            attachments.entity("status")?.let { e ->
                e.components.get<TransformComponent>()?.setPosition(Vector3(0f, 0.55f, 0.36f))
                spatialContent.addEntity(e)
                scene.setStatusEntity(e)
            }
            content = spatialContent
            Log.i(BoardScene.TAG, "SCENE_ATTACHED")
        },
    )
}

/** Compact status plaque floating over the board: whose turn, check, engine thinking. */
@Composable
private fun StatusPlaque(state: BoardUiState) {
    val strings = LocalStrings.current
    val (label, dot) = when {
        state.phase == Phase.SETUP -> strings.setup to XiangqiColors.amber
        state.phase == Phase.REVIEW -> strings.reviewing to XiangqiColors.amber
        state.engineThinking -> strings.thinking to XiangqiColors.amber
        state.sideToMove == Side.RED -> strings.redToMove to XiangqiColors.cinnabar
        else -> strings.blackToMove to XiangqiColors.ink
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .backgroundMaterial(enable = true, style = Material.Regular)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Text(label, style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary)
        if (state.checkedKing != null && state.phase != Phase.SETUP) {
            Text(strings.check, style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.error)
        }
        if (state.vsEngine && state.phase == Phase.PLAY) {
            Text(strings.engineLevelLabel(state.settings.level), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
        }
    }
}
