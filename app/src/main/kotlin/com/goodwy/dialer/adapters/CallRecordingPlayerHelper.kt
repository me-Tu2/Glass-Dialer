package com.goodwy.dialer.adapters
import android.app.AlertDialog
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

object CallRecordingPlayerHelper {
    data class RecEntry(val uri: Uri, val name: String, val tsMs: Long)
    private var cache: List<RecEntry> = emptyList()
    private var lastScan = 0L

    private fun getRecordings(ctx: Context): List<RecEntry> {
        val now = System.currentTimeMillis()
        if (cache.isNotEmpty() && now - lastScan < 3000L) return cache
        val list = mutableListOf<RecEntry>()
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val rx = Regex("""(\d{8}_\d{6})""")
        val proj = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) proj.add(MediaStore.Audio.Media.RELATIVE_PATH)
        try {
            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj.toTypedArray(), null, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nmC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val adC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val ptC = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
                while (c.moveToNext()) {
                    val name = c.getString(nmC) ?: continue
                    val path = if (ptC >= 0) c.getString(ptC).orEmpty() else ""
                    if (!path.contains("GlassDialer Recordings", true)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(idC))
                    val m = rx.find(name)
                    val ms = m?.let { runCatching { fmt.parse(it.groupValues[1])?.time }.getOrNull() } ?: (c.getLong(adC) * 1000L)
                    list.add(RecEntry(uri, name.removeSuffix(".m4a"), ms))
                }
            }
        } catch (_: Exception) {}
        cache = list; lastScan = now; return list
    }

    fun bindPlayButton(ctx: Context, btn: ImageView?, startRaw: Long, durSec: Long) {
        if (btn == null) return
        val startMs = if (startRaw < 10_000_000_000L) startRaw * 1000L else startRaw
        val endMs = startMs + durSec.coerceAtLeast(1L) * 1000L
        val match = getRecordings(ctx).filter { it.tsMs in (startMs - 15_000L)..(endMs + 30_000L) }.minByOrNull { abs(it.tsMs - endMs) }
        if (match != null) {
            btn.visibility = View.VISIBLE
            btn.setOnClickListener { showPlayer(ctx, match) { lastScan = 0L; btn.visibility = View.GONE } }
        } else { btn.visibility = View.GONE; btn.setOnClickListener(null) }
    }

    private fun fmtTime(ms: Int): String { val s = (ms / 1000).coerceAtLeast(0); return String.format(Locale.US, "%02d:%02d", s / 60, s % 60) }
    private fun showPlayer(ctx: Context, entry: RecEntry, onDel: () -> Unit) {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        var mp: MediaPlayer? = null
        val h = Handler(Looper.getMainLooper())
        var playing = false
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(18))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.parseColor("#E61C1C1E")); setStroke(dp(1), Color.parseColor("#33FFFFFF")) }
        }
        val title = TextView(ctx).apply { text = entry.name; setTextColor(Color.WHITE); textSize = 16f; setTypeface(null, Typeface.BOLD); maxLines = 1 }
        val sub = TextView(ctx).apply { text = "Call Recording"; setTextColor(Color.parseColor("#99EBEBF5")); textSize = 12f; setPadding(0, dp(2), 0, dp(14)) }
        val seek = SeekBar(ctx)
        val row = LinearLayout(ctx).apply { setPadding(0, dp(2), 0, dp(14)) }
        val curT = TextView(ctx).apply { text = "00:00"; setTextColor(Color.parseColor("#CCFFFFFF")); textSize = 12f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val totT = TextView(ctx).apply { text = "00:00"; setTextColor(Color.parseColor("#CCFFFFFF")); textSize = 12f; gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        row.addView(curT); row.addView(totT)
        val btns = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        val ppBtn = TextView(ctx).apply { text = "Pause"; setTextColor(Color.WHITE); textSize = 14f; setTypeface(null, Typeface.BOLD); setPadding(dp(24), dp(10), dp(24), dp(10)); background = GradientDrawable().apply { cornerRadius = dp(50).toFloat(); setColor(Color.parseColor("#FF0A84FF")) } }
        val delBtn = TextView(ctx).apply { text = "Delete"; setTextColor(Color.parseColor("#FFFF453A")); textSize = 14f; setTypeface(null, Typeface.BOLD); setPadding(dp(20), dp(10), dp(20), dp(10)); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) }; background = GradientDrawable().apply { cornerRadius = dp(50).toFloat(); setColor(Color.parseColor("#26FF453A")) } }
        btns.addView(ppBtn); btns.addView(delBtn)
        root.addView(title); root.addView(sub); root.addView(seek); root.addView(row); root.addView(btns)
        val dlg = AlertDialog.Builder(ctx).setView(root).create().apply { window?.setBackgroundDrawableResource(android.R.color.transparent) }
        val tick = object : Runnable { override fun run() { if (playing) { runCatching { val p = mp?.currentPosition ?: 0; seek.progress = p; curT.text = fmtTime(p) }; h.postDelayed(this, 250L) } } }
        try {
            mp = MediaPlayer().apply { setDataSource(ctx, entry.uri); prepare(); seek.max = duration; totT.text = fmtTime(duration); start(); playing = true; setOnCompletionListener { playing = false; ppBtn.text = "Play"; seek.progress = 0; curT.text = "00:00" } }
            h.post(tick)
        } catch (_: Exception) { Toast.makeText(ctx, "Unable to play", Toast.LENGTH_SHORT).show(); return }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) { runCatching { mp?.seekTo(p) }; curT.text = fmtTime(p) } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        ppBtn.setOnClickListener { val p = mp ?: return@setOnClickListener; if (playing) { p.pause(); playing = false; ppBtn.text = "Play" } else { p.start(); playing = true; ppBtn.text = "Pause"; h.post(tick) } }
        delBtn.setOnClickListener { runCatching { ctx.contentResolver.delete(entry.uri, null, null); Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show(); onDel() }; dlg.dismiss() }
        dlg.setOnDismissListener { playing = false; h.removeCallbacks(tick); runCatching { mp?.stop() }; runCatching { mp?.release() }; mp = null }
        dlg.show()
    }
}
