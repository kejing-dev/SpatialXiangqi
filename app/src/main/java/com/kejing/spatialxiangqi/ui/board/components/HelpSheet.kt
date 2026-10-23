package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Sheet

/** First-run / help sheet: three steps, one button. */
@Composable
fun HelpSheet(onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    Sheet(
        modifier = Modifier.width(600.dp),
        onDismissRequest = { onEvent(BoardEvent.HelpDone) },
        title = {
            // design-style: inherited-content-color Sheet
            Text(s.helpTitle, style = PicoTheme.typography.headlineMedium)
        },
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(s.helpStart) { onEvent(BoardEvent.HelpDone) }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            s.helpSteps.forEach { step ->
                Text(step, style = PicoTheme.typography.bodyLarge, color = PicoTheme.colorScheme.labelPrimary)
            }
            Text(s.hintTapPiece, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
        }
    }
}
