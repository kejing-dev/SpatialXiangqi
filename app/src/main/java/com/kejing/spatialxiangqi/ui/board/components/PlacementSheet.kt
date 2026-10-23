package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Sheet

/** 摆放与显示: mode, fine-tuning and display switches. Whole-window placement is the system handle. */
@Composable
fun PlacementSheet(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    val st = state.settings
    Sheet(
        modifier = Modifier.width(640.dp),
        onDismissRequest = { onEvent(BoardEvent.ClosePanel) },
        title = {
            // design-style: inherited-content-color Sheet
            Text(s.placementTitle, style = PicoTheme.typography.headlineMedium)
        },
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(s.done) { onEvent(BoardEvent.ClosePanel) }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.placementNote, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.padding(bottom = 8.dp))
            SettingRow("${s.wall} / ${s.table}") {
                TextSegments(listOf(s.wall, s.table), if (st.mode == BoardMode.WALL) 0 else 1) {
                    onEvent(BoardEvent.SetMode(if (it == 0) BoardMode.WALL else BoardMode.TABLE))
                }
            }
            SettingRow(s.boardSize, "${st.boardScalePercent}%") {
                Slider(
                    value = st.boardScalePercent.toFloat(),
                    onValueChange = { onEvent(BoardEvent.SetBoardScale(it.toInt())) },
                    valueRange = 80f..140f,
                    modifier = Modifier.width(240.dp),
                )
            }
            if (st.mode == BoardMode.TABLE) {
                SettingRow(s.orientation) {
                    TextSegments(listOf("0°", "90°", "180°", "270°"), (st.orientationDeg / 90) % 4) { onEvent(BoardEvent.SetOrientation(it * 90)) }
                }
                SettingRow(s.tilt, "${st.tiltDeg}°") {
                    Slider(
                        value = st.tiltDeg.toFloat(),
                        onValueChange = { onEvent(BoardEvent.SetTilt(it.toInt())) },
                        valueRange = 0f..20f,
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
            SettingRow(s.heightTrim) {
                TextSegments(listOf("−1 cm", "0", "+1 cm"), st.heightTrimCm + 1) { onEvent(BoardEvent.SetHeightTrim(it - 1)) }
            }
            SettingRow(s.showCoords) { Switch(checked = st.showCoordinates, onCheckedChange = { onEvent(BoardEvent.SetShowCoords(it)) }) }
            SettingRow(s.reduceMotion) { Switch(checked = st.reduceMotion, onCheckedChange = { onEvent(BoardEvent.SetReduceMotion(it)) }) }
        }
    }
}
