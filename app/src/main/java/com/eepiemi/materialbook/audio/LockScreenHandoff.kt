package com.eepiemi.materialbook.audio

import org.json.JSONObject
import org.json.JSONTokener

/** The PiP video's state as snapshotted when the page was hidden at lock. */
data class HandoffRead(
    val src: String,
    val positionMs: Long,
    /** Whether the video was playing right before the page was hidden. */
    val wasPlaying: Boolean,
)

/**
 * A request to run pipHandbackJs; [seq] keeps two identical requests distinct
 * as Compose effect keys.
 */
data class PipHandback(
    val seq: Int,
    val positionMs: Long?,
    val play: Boolean,
    /** Re-install the handoff listener afterwards (still in PiP). */
    val rearm: Boolean,
)

/**
 * Only a plain https URL that was actually playing is handed off: a blob:
 * (MSE) URL can't be played by ExoPlayer, and a video the user had paused
 * shouldn't start playing on the lock screen.
 */
fun isHandoffEligible(src: String?, wasPlaying: Boolean): Boolean =
    !src.isNullOrEmpty() && src.startsWith("https://") && wasPlaying

/**
 * Parses the raw evaluateJavascript result for PIP_HANDOFF_READ_JS. That
 * result is the script's return value JSON-encoded once more, i.e. a quoted
 * string holding the JSON object. Returns null for anything unexpected.
 */
fun parseHandoffRead(raw: String?): HandoffRead? = runCatching {
    val json = JSONTokener(raw ?: return null).nextValue() as? String ?: return null
    val obj = JSONObject(json)
    HandoffRead(
        src = obj.optString("src", ""),
        positionMs = (obj.optDouble("time", 0.0) * 1000).toLong().coerceAtLeast(0L),
        wasPlaying = obj.optBoolean("wasPlaying", false),
    )
}.getOrNull()
