package com.kejing.spatialxiangqi

import com.kejing.spatialxiangqi.ui.board.BoardScreen
import com.kejing.spatialxiangqi.ui.theme.XiangqiTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

/** Entry chain: volumetric DefaultWindowContainer → PicoTheme (XiangqiTheme) → BoardScreen. */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            XiangqiTheme {
                BoardScreen()
            }
        }
    }
