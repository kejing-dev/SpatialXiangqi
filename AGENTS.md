<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:start -->
## Plugin Context

Also read `./PICO-SPATIAL-AGENTIC-TOOLS.AGENTS.md` for PICO Spatial plugin guidance.
<!-- pico-cli:plugin-context:pico-spatial-agentic-tools:end -->

## Repository layout（空间象棋 · Spatial Xiangqi，2026-09-22）

Product name 空间象棋 / Spatial Xiangqi; Android package `com.kejing.spatialxiangqi`; Gradle root `SpatialXiangqi`, module `:app`.

- `app/` Kotlin + PICO Spatial SDK BOM 6.1.9 (Spatial UI Compose, ECS). No Material/Material3.
- `app/src/main/java/com/kejing/spatialxiangqi/`
  - `platform/` `SpatialApplication`, `LaunchActivity`, `DebugReceiver` (adb-driven state channel, debug builds only).
  - `domain/` pure Kotlin: `model` (Side, PieceType, Piece, Square, Move, Position/FEN, GameTree, GameRecord), `rules` (move generator, legality, setup validation; perft-verified), `notation` (Chinese / WXF / ICCS, PGN import/export), `engine` (alpha-beta `LocalEngine`, 5 levels), `usecase`.
  - `data/repository/` `SettingsRepository` (SharedPreferences) and `GameRepository` (JSON files under `files/games/`) with `Fake*` twins for tests.
  - `ui/board/` `BoardUiState` + `BoardEvent`, `BoardViewModel` (single source of truth), `BoardScreen` (stateful shell + debug command mapping), `components/`:
    `BoardScene` (procedural 3D board: slab, textured face, ledge, eval rail, 32 pieces, 90 tap pads, highlight marks), `BoardTextures` (Canvas painter, Noto Sans), `Board3D` (SpatialView + tap gesture + status attachment panel), `BoardToolbar`, `ScoreScroll` (Subwindow), `BoxSheet`, `PlacementSheet`, `HelpSheet`, `Dialogs`, `Common`.
  - `ui/theme/` `XiangqiTheme` (16-role ColorScheme + Noto Sans typography), `Skins` (紫檀 / 竹林 / 楚汉烽烟 tokens). `ui/i18n/Strings.kt` bilingual copy (zh full-width punctuation).
- `app/src/main/res/font/notosanssc.ttf` (variable Noto Sans SC), `res/drawable/ic_*.xml` Material-path icons.
- `app/src/main/AndroidManifest.xml` owns the volumetric window: `1.1x1.05x0.9` meters, WorldScale.Fixed, gravity aligned, no material background, base panel hidden.
- `docs/verification/` emulator captures (PICO Emulator 6.1, `emulator-5554`). `releases/` shipped debug APK. `.scratch/` receipts + validation results.
- Design package (not in this repo): `/Users/kejing/Downloads/SpatialXiangqi/design/` (design-doc.md, preview.html, figma-frames/). PRD: https://claude.ai/code/artifact/4e312d03-bca2-49f8-9d8e-559397530d05

## Build / run

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # 68 JVM tests (rules, notation, PGN, engine, tree, use cases, ViewModel)
PICO_HOME=/Users/kejing/Library/PICO/sdk pico-cli app install app/build/outputs/apk/debug/app-debug.apk -r -d emulator-5554
pico-cli app launch com.kejing.spatialxiangqi -d emulator-5554
pico-cli capture screenshot -d emulator-5554 -o shot.png
```

Machine gates: `bash <plugin>/skills/spatial-design-to-app/scripts/validate_workflow_and_build.sh . --input-mode product_doc --generation-mode new_project --visual-asset false --design-color labelPrimaryLight=#F7F1E4 --design-color error=#D64545 --design-color alert=#E8A33D --design-color passable=#3E9B6A --design-color interaction=#E8A33D` (all 8 passed on 2026-09-22).

## Emulator debug channel

The emulator cannot inject spatial taps, so every state is driven through the ViewModel event path:

```
adb shell am broadcast -n com.kejing.spatialxiangqi/.platform.DebugReceiver -a com.kejing.spatialxiangqi.DEBUG --es cmd <cmd> [--es arg <arg>]
```

`tap b2`, `move b2e2`, `undo`, `redo`, `hint`, `engine on|off`, `mode wall|table`, `lang zh|en`, `skin rosewood|bamboo|battlefield`, `face glyph|letter`, `notation 0|1|2`, `panel box|placement|help|none`, `section library|new|io|settings`, `newgame red|black|random[:level[:off]]`, `review`, `play_here`, `setup`, `setup_pick K|a|…`, `setup_done`, `resign`, `save`, `jump N`, `back|forward|start|end`, `help_done`, `import <pgn>`, `export`, `scale N`, `tilt N`, `orient N`, `coords on|off`, `dismiss`, `state` (logs to `XiangqiDebug`), `tune key=value` (scene knobs: `wallPitch`, `planePitch`, `flipX`, `flipY`, `frontZ`, `wallY`, `tableY`, `tableZ`, `lift`, `status`, `statusScale`).

## Scene facts verified on the emulator

- SpatialView local origin is the volume centre; +Z faces the viewer; `createPlane` is vertical (XY) so flat planes get pitch −90°; `createCylinder` axis is Y.
- Every SDK resource call (`MeshResource.create*`, `TextureResource.create`) is a blocking ~1 s runtime round-trip on the emulator: `BoardScene.createResources` runs them concurrently on `Dispatchers.IO`; entity creation stays on main. Never create resources on the main thread (it ANRs).
- Alpha-cut piece faces use `BlendingMode.MASKED` + `setOpacityThreshold`; highlight marks use unlit `TRANSPARENT` materials.
- Toolbar sits at the bottom-front edge, Subwindow on the right side of the container, Sheets/AlertDialogs float in front of the board.

## v0.1.1 (2026-09-24, device feedback)

- Label roles pinned to ink tones (`XiangqiColors.label*`) because Vibrant labels read grey on light glass in a dim room.
- Score scroll toggle (`BoardEvent.ToggleScroll` / `SetScrollOpen`, debug `scroll on|off`), `offset = DpOffset3D(-56.dp, 0, 0)`; current move rendered as an amber chip.
- Table tilt: positive pitch lifts the far edge; the slab is raised by (H/2)·sin(tilt) so the near edge stays on the volume floor.

## Known gaps

- Real hand/controller ray taps and the system window handle were not exercised (emulator has no spatial input).
- Table mode: the Subwindow is anchored to the 1.05 m container, so it floats above the flat board; runtime container resize is not used. Since v0.1.1 the scroll can be hidden (toolbar 棋谱 / its × button) and sits 56 dp closer to the board; the container is 0.96 m wide.
- No sounds yet (the switch is persisted only); no move animation.
