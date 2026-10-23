package com.kejing.spatialxiangqi.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * adb-driven debug channel for emulator verification (the PICO emulator has no scripted
 * spatial input). All commands go through the same ViewModel event path as real input:
 *
 *   adb shell am broadcast -n com.kejing.spatialxiangqi/.platform.DebugReceiver -a com.kejing.spatialxiangqi.DEBUG --es cmd <cmd> --es arg <arg>
 *
 * cmd: move (arg "h2e2"), select (arg "h2"), tap_square (arg "e2"), undo, hint, mode (wall|table),
 *      lang (zh|en), skin (rosewood|bamboo|battlefield), panel (box|placement|help|none),
 *      section (library|new|io|settings), newgame (arg "red:3"), engine (on|off), review, setup,
 *      resign, notation (0|1|2), face (glyph|letter), state (logs the current state).
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val arg = intent.getStringExtra("arg") ?: ""
        Log.i(TAG, "debug cmd=$cmd arg=$arg")
        commands.tryEmit(DebugCommand(cmd, arg))
    }

    companion object {
        const val TAG = "XiangqiDebug"
        const val ACTION = "com.kejing.spatialxiangqi.DEBUG"
        val commands = MutableSharedFlow<DebugCommand>(extraBufferCapacity = 16)
    }
}

data class DebugCommand(val cmd: String, val arg: String)
