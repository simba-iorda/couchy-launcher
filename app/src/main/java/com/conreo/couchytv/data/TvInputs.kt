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
    /** physical HDMI/AV port number when it can be resolved (0 = unknown) */
    val port: Int,
    /** how the port was resolved: 2 = bundle extra, 1 = label, 0 = id suffix */
    val src: Int,
)

private class Merged(
    val canonical: Raw,
    var altId: String,
    /** label of the CEC twin collapsed into this entry (empty if none) */
    var twinLabel: String,
)

/** Passthrough inputs (HDMI/AV/…) sorted by label; multiple entries pointing at
 *  the same physical port (e.g. the HDMI-CEC "PlayStation 5" entry generated as
 *  …/HDMI100004 twins with the plain "HDMI 1") collapsed into one. Empty on
 *  devices without the TV Input Framework (Fire TV). */
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
            val suffix = info.id.substringAfterLast('/')
            // passthrough TV inputs expose the physical port in a bundle extra
            // on many OEMs (e.g. Medi­aTek's HDMI_PORT_ID)
            var port = 0
            var src = 0
            runCatching {
                info.extras?.let { b ->
                    for (k in b.keySet()) {
                        if (!k.uppercase().contains("PORT")) continue
                        val v = b.get(k)
                        val n = when (v) {
                            is Int -> v
                            is Long -> if (v in 1..32) v.toInt() else null
                            is String -> v.trim().toIntOrNull()
                            else -> null
                        }
                        if (n != null && n in 1..32) {
                            port = n
                            src = 2
                        }
                    }
                }
            }
            if (port == 0) {
                // user-visible port names like "HDMI 1" / "AV 2"
                Regex("^HDMI\\s*([1-9])\\s*$|^AV\\s*([1-9])\\s*$", RegexOption.IGNORE_CASE)
                    .find(label.trim())?.let { m ->
                        (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
                            ?.let { port = it; src = 1 }
                    }
            }
            if (port == 0) {
                // HDMI-CEC entries are generated as HDMI<hdmiPhysicalAddressHex><cecLogicalAddressHex>
                // (e.g. …/HDMI100004 = device on port 1): first digit = port.
                Regex("^HDMI([1-9])([0-9A-Fa-f]{2,})?$").find(suffix)?.let { m ->
                    port = m.groupValues[1].toInt()
                    src = 0
                }
            }
            raw += Raw(info, "${info.serviceInfo.packageName}/${info.serviceInfo.name}", label, connected, port, src)
        }
    }

    // Collapse entries of the same service group that resolve to the same
    // physical port. Entries without any port signal keep their own id and
    // are never merged.
    val merged = LinkedHashMap<String, Merged>()
    for (entry in raw) {
        val key = entry.service + "#" + (if (entry.port == 0) "id:" + entry.info.id else entry.port)
        val prev = merged[key]
        if (prev == null) {
            merged[key] = Merged(entry, "", "")
            continue
        }
        if (entry.src > prev.canonical.src) {
            // Promote the steadier source (bundle-extra port entry beats a
            // CEC-generated id): the hardware id survives device power-off.
            val twin = prev.canonical
            merged[key] = Merged(
                entry,
                twin.info.id,
                if (twin.connected && twin.label != entry.label) twin.label else prev.twinLabel,
            )
        } else {
            if (entry.connected && entry.label != prev.canonical.label && prev.twinLabel.isEmpty()) {
                prev.twinLabel = entry.label
            }
            if (prev.altId.isEmpty() && entry.info.id != prev.canonical.info.id) {
                prev.altId = entry.info.id
            }
        }
    }
    return merged.values.map { m ->
        val label = if (m.twinLabel.isNotEmpty()) "${m.canonical.label} (${m.twinLabel})" else m.canonical.label
        TvInputItem(m.canonical.info.id, m.altId, label, m.canonical.connected)
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
