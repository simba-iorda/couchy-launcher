package com.conreo.couchytv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build

data class TvInputItem(
    val id: String,
    /** Twin id that points to the same physical port (HDMI-CEC input); empty if none. */
    val altId: String,
    val label: String,
    val connected: Boolean,
)

private class Raw(
    val info: TvInputInfo,
    val service: String,
    val label: String,
    val connected: Boolean,
)

private class Merged(
    val canonicalId: String,
    var altId: String,
    val label: String,
    val connected: Boolean,
    /** label of the CEC twin collapsed into this entry (empty if none) */
    var twinLabel: String,
)

/** Passthrough inputs (HDMI/AV/…) sorted by label; multiple entries pointing at the
 *  same physical port are collapsed into one (e.g. the HDMI-CEC "PlayStation 5"
 *  entry twins with the plain "HDMI 1" input). Empty on devices without TIF (Fire TV). */
fun Context.listHardwareInputs(): List<TvInputItem> {
    val mgr = runCatching {
        getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    }.getOrNull() ?: return emptyList()

    val raw = ArrayList<Raw>()
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
            raw += Raw(info, "${info.serviceInfo.packageName}/${info.serviceInfo.name}", label, connected)
        }
    }

    // Group entries of one service by physical port. The port number comes
    // from the label ("HDMI 1") or from the id suffix: HDMI-CEC inputs are
    // generated as HDMI<hdmiPhysicalAddressHex><cecLogicalAddressHex>
    // (e.g. …/HDMI100004 = PlayStation 5 on port 1), so the digit right
    // after "HDMI" is the port number.
    val merged = LinkedHashMap<String, Merged>()
    for (entry in raw) {
        val suffix = entry.info.id.substringAfterLast('/')
        val port = Regex("^HDMI\\s*([1-9])\\s*$", RegexOption.IGNORE_CASE)
            .find(entry.label.trim())?.groupValues?.get(1)
            ?: Regex("^HDMI([1-9])([0-9A-Fa-f]{2,})?$")
                .find(suffix)?.groupValues?.get(1)
        // Without a port number the entry stays unique (never merged).
        val key = entry.service + "#" + (port ?: "id:$suffix")
        val prev = merged[key]
        if (prev == null) {
            merged[key] = Merged(entry.info.id, "", entry.label, entry.connected, "")
            continue
        }
        val entryIsHw = Regex("^HW\\d+$").matches(suffix)
        val canonicalIsHw = Regex("^HW\\d+$").matches(prev.canonicalId.substringAfterLast('/'))
        if (entryIsHw && !canonicalIsHw) {
            // Swap: the stable hardware-port id becomes canonical, the CEC
            // generated id demotes to the alternate.
            merged[key] = Merged(
                entry.info.id,
                prev.canonicalId,
                entry.label,
                entry.connected,
                if (entry.connected && prev.label != entry.label) prev.label else "",
            )
        } else {
            if (entry.connected && entry.label != prev.label && prev.twinLabel.isEmpty()) {
                prev.twinLabel = entry.label
            }
            if (prev.altId.isEmpty() && entry.info.id != prev.canonicalId) {
                prev.altId = entry.info.id
            }
        }
    }
    return merged.values.map { m ->
        val label = if (m.twinLabel.isNotEmpty()) "${m.label} (${m.twinLabel})" else m.label
        TvInputItem(m.canonicalId, m.altId, label, m.connected)
    }.sortedBy { it.label.lowercase() }
}

/** Switch the TV to the given input. Returns false if nothing handled the intent. */
fun Context.switchToTvInput(inputId: String, altId: String): Boolean {
    // Direct switch — the system TV app tunes the passthrough input. Some OEMs
    // only route the CEC-generated id, others only the plain port id, so try
    // both before falling back to the system input picker.
    if (switchDirect(inputId)) return true
    if (altId.isNotEmpty() && altId != inputId) {
        if (switchDirect(altId)) return true
    }
    // The system input-picker screen (same as the remote's Input button)
    if (tryStart(Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
    // Official sources screen (API 24+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        tryStart(Intent(TvInputManager.ACTION_SETUP_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    ) return true
    return false
}

private fun Context.switchDirect(inputId: String): Boolean =
    tryStart(Intent(
        Intent.ACTION_VIEW,
        TvContract.buildChannelUriForPassthroughInput(inputId),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun Context.tryStart(intent: Intent): Boolean = runCatching {
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        true
    } else false
}.getOrDefault(false)
