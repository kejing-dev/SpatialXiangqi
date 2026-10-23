package com.kejing.spatialxiangqi.ui.board.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kejing.spatialxiangqi.R
import com.kejing.spatialxiangqi.domain.model.GameRecord
import com.kejing.spatialxiangqi.domain.model.Language
import com.kejing.spatialxiangqi.domain.model.PieceFace
import com.kejing.spatialxiangqi.domain.model.Skin
import com.kejing.spatialxiangqi.ui.board.BoardEvent
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.i18n.LocalStrings
import com.kejing.spatialxiangqi.ui.navigation.BoxSection
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SearchField
import com.pico.spatial.ui.design.SideNavigation
import com.pico.spatial.ui.design.SideNavigationItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextArea
import com.pico.spatial.ui.design.windows.Sheet

/** 棋箱: library / new game / import-export / settings in one modal sheet with side navigation. */
@Composable
fun BoxSheet(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    Sheet(
        modifier = Modifier.width(880.dp).height(600.dp),
        onDismissRequest = { onEvent(BoardEvent.ClosePanel) },
        title = {
            // design-style: inherited-content-color Sheet
            Text(s.box, style = PicoTheme.typography.headlineMedium)
        },
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            SideNavigation(modifier = Modifier.width(176.dp).fillMaxHeight()) {
                NavItem(s.library, R.drawable.ic_library, state.boxSection == BoxSection.LIBRARY) { onEvent(BoardEvent.SetBoxSection(BoxSection.LIBRARY)) }
                NavItem(s.newGame, R.drawable.ic_add, state.boxSection == BoxSection.NEW_GAME) { onEvent(BoardEvent.SetBoxSection(BoxSection.NEW_GAME)) }
                NavItem(s.importExport, R.drawable.ic_import, state.boxSection == BoxSection.IMPORT_EXPORT) { onEvent(BoardEvent.SetBoxSection(BoxSection.IMPORT_EXPORT)) }
                NavItem(s.settings, R.drawable.ic_settings, state.boxSection == BoxSection.SETTINGS) { onEvent(BoardEvent.SetBoxSection(BoxSection.SETTINGS)) }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.boxSection) {
                    BoxSection.LIBRARY -> LibrarySection(state, onEvent)
                    BoxSection.NEW_GAME -> NewGameSection(state, onEvent)
                    BoxSection.IMPORT_EXPORT -> ImportExportSection(state, onEvent)
                    BoxSection.SETTINGS -> SettingsSection(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    SideNavigationItem(
        selected = selected,
        modifier = Modifier.fillMaxWidth().spatialClickable(onClick = onClick),
        leading = { Icon(painterResource(iconRes), contentDescription = label, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelPrimary) },
    ) {
        // design-style: inherited-content-color SideNavigationItem
        Text(label, style = PicoTheme.typography.labelLarge)
    }
}

@Composable
private fun ColumnScope.LibrarySection(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    SearchField(
        value = state.libraryQuery,
        onValueChange = { onEvent(BoardEvent.SetLibraryQuery(it)) },
        onSearch = { onEvent(BoardEvent.SetLibraryQuery(it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(s.library, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelTertiary) },
    )
    if (state.library.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(s.emptyLibrary, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelTertiary)
        }
    } else {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.library, key = { it.id }) { game -> LibraryRow(game, onEvent) }
        }
    }
}

@Composable
private fun LibraryRow(game: GameRecord, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    val result = when (game.result) {
        GameRecord.RESULT_RED -> s.redWins
        GameRecord.RESULT_BLACK -> s.blackWins
        GameRecord.RESULT_DRAW -> s.draw
        else -> ""
    }
    val summary = listOf(result, "${(game.plyCount + 1) / 2} ${s.moves}", game.date).filter { it.isNotBlank() }.joinToString(" · ")
    ListItem(
        headlineContent = {
            // design-style: inherited-content-color ListItem
            Text(game.title.ifBlank { s.untitled }, style = PicoTheme.typography.bodyLarge, maxLines = 1)
        },
        modifier = Modifier.fillMaxWidth().spatialClickable { onEvent(BoardEvent.OpenGame(game.id)) },
        supportingContent = {
            // design-style: inherited-content-color ListItem
            Text(summary, style = PicoTheme.typography.bodySmall, maxLines = 1)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryButton(s.review) { onEvent(BoardEvent.OpenGame(game.id)) }
                FlatIconButton(R.drawable.ic_delete, s.delete, size = 22.dp) { onEvent(BoardEvent.RequestDeleteGame(game.id)) }
            }
        },
    )
}

@Composable
private fun NewGameSection(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    SectionTitle(s.newGame)
    SettingBlock(s.side) { TextSegments(listOf(s.playRed, s.playBlack, s.playRandom), state.draftSide, Modifier.fillMaxWidth()) { onEvent(BoardEvent.SetDraftSide(it)) } }
    SettingRow(s.engine) { Switch(checked = state.draftEngine, onCheckedChange = { onEvent(BoardEvent.SetDraftEngine(it)) }) }
    SettingBlock(s.level) { TextSegments(s.levels, state.draftLevel - 1, Modifier.fillMaxWidth()) { onEvent(BoardEvent.SetDraftLevel(it + 1)) } }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        PrimaryButton(s.start) { onEvent(BoardEvent.StartNewGame) }
    }
}

@Composable
private fun ImportExportSection(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    SectionTitle(s.import)
    TextArea(
        value = state.importText,
        onValueChange = { onEvent(BoardEvent.SetImportText(it)) },
        modifier = Modifier.fillMaxWidth().height(120.dp),
        placeholder = { Text(s.pasteHint, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelTertiary) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        PrimaryButton(s.import, enabled = state.importText.isNotBlank()) { onEvent(BoardEvent.Import) }
    }
    SectionTitle(s.export)
    TextArea(
        value = state.exportText,
        onValueChange = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth().height(96.dp),
        placeholder = { Text("PGN", style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelTertiary) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        SecondaryButton(s.export, enabled = state.moves.isNotEmpty()) { onEvent(BoardEvent.Export) }
    }
}

@Composable
private fun SettingsSection(state: BoardUiState, onEvent: (BoardEvent) -> Unit) {
    val s = LocalStrings.current
    val st = state.settings
    SectionTitle(s.settings)
    SettingBlock(s.language) { TextSegments(listOf("中文", "English"), if (st.language == Language.ZH) 0 else 1, Modifier.fillMaxWidth()) { onEvent(BoardEvent.SetLanguage(if (it == 0) Language.ZH else Language.EN)) } }
    SettingBlock(s.notation) { TextSegments(listOf(s.notationChinese, s.notationWxf, s.notationIccs), st.notation, Modifier.fillMaxWidth()) { onEvent(BoardEvent.SetNotation(it)) } }
    SettingBlock(s.pieceFaces) { TextSegments(listOf(s.pieceFacesGlyph, s.pieceFacesLetters), if (st.pieceFace == PieceFace.GLYPH) 0 else 1, Modifier.fillMaxWidth()) { onEvent(BoardEvent.SetPieceFace(if (it == 0) PieceFace.GLYPH else PieceFace.LETTER)) } }
    SettingBlock(s.skin) {
        TextSegments(listOf(s.skinRosewood, s.skinBamboo, s.skinBattlefield), st.skin.ordinal, Modifier.fillMaxWidth()) { onEvent(BoardEvent.SetSkin(Skin.entries[it])) }
    }
    SettingRow(s.sounds) { Switch(checked = st.sounds, onCheckedChange = { onEvent(BoardEvent.SetSounds(it)) }) }
    SettingRow(s.haptics) { Switch(checked = st.haptics, onCheckedChange = { onEvent(BoardEvent.SetHaptics(it)) }) }
}
