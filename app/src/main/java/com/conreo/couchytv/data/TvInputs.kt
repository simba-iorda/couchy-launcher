package com.conreo.couchytv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build

data class TvInputItem(
    val id: String,
    val label: String,
    val connected: Boolean,
)

/** Passthrough inputs (HDMI/AV/…) sorted by label. Empty on devices without TIF (Fire TV). */
fun Context.listHardwareInputs(): List<TvInputItem> {
    val mgr = runCatching {
        getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    }.getOrNull() ?: return emptyList()
    val out = ArrayList<TvInputItem>()
    runCatching {
        for (info in mgr.tvInputList) {
            if (!info.isPassthroughInput()) continue
            if (info.type == TvInputInfo.TYPE_OTHER) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(this)) continue
            val label = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                info.loadCustomLabel(this)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: info.loadLabel(this)?.toString()?.takeIf { it.isNotBlank() }
                    ?: "Input"
            } else {
                info.loadLabel(this)?.toString()?.takeIf { it.isNotBlank() } ?: "Input"
            }
            val connected = runCatching {
                mgr.getInputState(info.id) != TvInputManager.INPUT_STATE_DISCONNECTED
            }.getOrDefault(true)
            out += TvInputItem(info.id, label, connected)
        }
    }
    return out.sortedBy { it.label.lowercase() }
}

/** Switch the TV to the given input. Returns false if nothing handled the intent. */
fun Context.switchToTvInput(inputId: String): Boolean {
    // 1) direct switch — the system TV app tunes the passthrough input
    val direct = Intent(
        Intent.ACTION_VIEW,
        TvContract.buildChannelUriForPassthroughInput(inputId),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (tryStart(direct)) return true
    // 2) the system input-picker screen (same as the remote's Input button)
    if (tryStart(Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
    // 3) official sources screen (API 24+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        tryStart(Intent(TvInputManager.ACTION_SETUP_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    ) return true
    return false
}

private fun Context.tryStart(intent: Intent): Boolean = runCatching {
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        true
    } else false
}.getOrDefault(false)
