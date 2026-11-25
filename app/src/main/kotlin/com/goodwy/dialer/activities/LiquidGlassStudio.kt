package com.goodwy.dialer.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.google.android.material.tabs.TabLayout

object LiquidGlassStudio {
    private const val PREFS = "liquid_glass_prefs"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getHeightPct(ctx: Context) = prefs(ctx).getInt("height", 100)
    fun getWidthPct(ctx: Context) = prefs(ctx).getInt("width", 92)
    fun getSpacingDp(ctx: Context) = prefs(ctx).getInt("spacing", 12)
    fun getRadiusDp(ctx: Context) = prefs(ctx).getInt("radius", 32)
    fun getOpacityPct(ctx: Context) = prefs(ctx).getInt("opacity", 42)
    fun getBlurRadius(ctx: Context) = prefs(ctx).getInt("blur", 35)
    fun getDimPct(ctx: Context) = prefs(ctx).getInt("dim", 38)

    fun dp(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun createBarDrawable(ctx: Context, customOpacity: Int = -1, customRadius: Int = -1): GradientDrawable {
        val op = if (customOpacity >= 0) customOpacity else getOpacityPct(ctx)
        val rad = if (customRadius >= 0) customRadius else getRadiusDp(ctx)
        val alpha = (op * 2.55f).toInt().coerceIn(18, 245)
        val dark = isDark(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, rad.toFloat())
            if (dark) {
                setColor(Color.argb(alpha, 26, 28, 34))
                setStroke(dp(ctx, 1.2f).toInt(), Color.argb(95, 255, 255, 255))
            } else {
                setColor(Color.argb(alpha, 248, 251, 255))
                setStroke(dp(ctx, 1.5f).toInt(), Color.argb((alpha + 75).coerceAtMost(255), 255, 255, 255))
            }
        }
    }

    fun createLensDrawable(ctx: Context, customRadius: Int = -1): GradientDrawable {
        val rad = if (customRadius >= 0) customRadius else getRadiusDp(ctx)
        val dark = isDark(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, rad.toFloat())
            if (dark) {
                setColor(Color.argb(72, 255, 255, 255))
                setStroke(dp(ctx, 1f).toInt(), Color.argb(135, 255, 255, 255))
            } else {
                setColor(Color.argb(215, 255, 255, 255))
                setStroke(dp(ctx, 1.2f).toInt(), Color.argb(255, 255, 255, 255))
            }
        }
    }

    fun createPanelDrawable(ctx: Context): GradientDrawable {
        val op = (getOpacityPct(ctx) + 18).coerceAtMost(95)
        val rad = dp(ctx, getRadiusDp(ctx).toFloat())
        val alpha = (op * 2.55f).toInt().coerceIn(35, 248)
        val dark = isDark(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(rad, rad, rad, rad, 0f, 0f, 0f, 0f)
            if (dark) {
                setColor(Color.argb(alpha, 22, 24, 30))
                setStroke(dp(ctx, 1.2f).toInt(), Color.argb(85, 255, 255, 255))
            } else {
                setColor(Color.argb(alpha, 250, 252, 255))
                setStroke(dp(ctx, 1.5f).toInt(), Color.argb(230, 255, 255, 255))
            }
        }
    }

    fun applyToBottomBar(activity: Activity, tabLayout: TabLayout) {
        tabLayout.background = createBarDrawable(activity)
        tabLayout.setSelectedTabIndicatorHeight(0)
        tabLayout.elevation = dp(activity, 10f)
        val lp = tabLayout.layoutParams
        if (lp != null) {
            lp.width = (activity.resources.displayMetrics.widthPixels * getWidthPct(activity) / 100f).toInt()
            lp.height = dp(activity, 60f * getHeightPct(activity) / 100f).toInt()
            tabLayout.layoutParams = lp
        }
        val spacingPx = dp(activity, getSpacingDp(activity) / 2f).toInt()
        val selected = tabLayout.selectedTabPosition
        val lens = createLensDrawable(activity)
        for (i in 0 until tabLayout.tabCount) {
            val view = tabLayout.getTabAt(i)?.customView ?: continue
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.leftMargin = spacingPx
                it.rightMargin = spacingPx
                view.layoutParams = it
            }
            if (i == selected) {
                view.background = lens
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(180).start()
            } else {
                view.background = null
                view.scaleX = 1f
                view.scaleY = 1f
            }
        }
    }

    fun applyToDialpad(activity: Activity, glassPanel: View?) {
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.setTranslucent(true)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.setDimAmount((getDimPct(activity) / 100f).coerceIn(0f, 0.85f))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = getBlurRadius(activity)
            if (blur > 0) activity.window.setBackgroundBlurRadius(blur)
        }
        glassPanel?.background = createPanelDrawable(activity)
    }
    fun showStudioDialog(activity: Activity) {
        var h = getHeightPct(activity); var w = getWidthPct(activity); var sp = getSpacingDp(activity)
        var rad = getRadiusDp(activity); var op = getOpacityPct(activity); var bl = getBlurRadius(activity); var dm = getDimPct(activity)
        val dark = isDark(activity)
        val bgCard = if (dark) Color.parseColor("#16181D") else Color.parseColor("#F4F6FA")
        val fgText = if (dark) Color.WHITE else Color.parseColor("#111318")
        val subText = if (dark) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgCard)
            val pad = dp(activity, 18f).toInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(activity).apply {
            text = "Liquid Glass UI"; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(fgText); gravity = Gravity.CENTER
        })

        val previewBox = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(activity, 20f); setColor(if (dark) Color.parseColor("#252A34") else Color.parseColor("#DCE4F2")) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 110f).toInt()).apply {
                topMargin = dp(activity, 12f).toInt(); bottomMargin = dp(activity, 12f).toInt()
            }
        }
        val previewPill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(activity, 10f).toInt(), dp(activity, 6f).toInt(), dp(activity, 10f).toInt(), dp(activity, 6f).toInt())
        }
        val tabViews = listOf("Favorites", "Recents", "Contacts").mapIndexed { idx, label ->
            TextView(activity).apply {
                text = label; textSize = 12f; setTypeface(null, if (idx == 1) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(fgText); gravity = Gravity.CENTER
                setPadding(dp(activity, 12f).toInt(), dp(activity, 8f).toInt(), dp(activity, 12f).toInt(), dp(activity, 8f).toInt())
            }
        }
        tabViews.forEach { previewPill.addView(it) }
        previewBox.addView(previewPill)
        root.addView(previewBox)

        fun refreshPreview() {
            previewPill.background = createBarDrawable(activity, op, rad)
            previewPill.layoutParams = previewPill.layoutParams.apply { height = dp(activity, 52f * h / 100f).toInt() }
            tabViews.forEachIndexed { idx, tv ->
                (tv.layoutParams as LinearLayout.LayoutParams).apply {
                    leftMargin = dp(activity, sp / 4f).toInt(); rightMargin = dp(activity, sp / 4f).toInt()
                }
                tv.background = if (idx == 1) createLensDrawable(activity, rad) else null
            }
        }
        refreshPreview()

        val sliders = mutableListOf<() -> Unit>()
        fun addSlider(label: String, min: Int, max: Int, suffix: String, getter: () -> Int, setter: (Int) -> Unit) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(activity, 8f).toInt() }
            }
            val valTv = TextView(activity).apply { text = "${getter()}$suffix"; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(subText) }
            row.addView(TextView(activity).apply { text = label; textSize = 14f; setTextColor(fgText); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(valTv)
            root.addView(row)
            val seek = SeekBar(activity).apply {
                this.max = max - min; progress = (getter() - min).coerceIn(0, max - min)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { val v = min + p; setter(v); valTv.text = "$v$suffix"; refreshPreview() }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            root.addView(seek)
            sliders.add { val cur = getter(); seek.progress = (cur - min).coerceIn(0, max - min); valTv.text = "$cur$suffix" }
        }

        val presetScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val presetRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Original" to intArrayOf(100, 92, 12, 32, 42, 35, 38),
            "Soft" to intArrayOf(106, 94, 14, 36, 56, 48, 45),
            "Crystal" to intArrayOf(96, 90, 10, 30, 22, 25, 25),
            "Frosted" to intArrayOf(112, 96, 16, 28, 74, 68, 55)
        ).forEach { (name, vals) ->
            presetRow.addView(Button(activity).apply {
                text = name; isAllCaps = false; textSize = 12f
                setOnClickListener { h = vals[0]; w = vals[1]; sp = vals[2]; rad = vals[3]; op = vals[4]; bl = vals[5]; dm = vals[6]; sliders.forEach { it() }; refreshPreview() }
            })
        }
        presetScroll.addView(presetRow); root.addView(presetScroll)

        addSlider("Height", 70, 160, "%", { h }, { h = it })
        addSlider("Maximum width", 65, 100, "%", { w }, { w = it })
        addSlider("Button spacing", 0, 48, "dp", { sp }, { sp = it })
        addSlider("Corner roundness", 8, 48, "dp", { rad }, { rad = it })
        addSlider("Glass opacity", 5, 95, "%", { op }, { op = it })
        addSlider("Blur", 0, 80, "", { bl }, { bl = it })
        addSlider("Dim", 0, 85, "%", { dm }, { dm = it })

        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(root) })
            .setNeutralButton("Reset") { _, _ -> prefs(activity).edit().clear().apply() }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                prefs(activity).edit().putInt("height", h).putInt("width", w).putInt("spacing", sp)
                    .putInt("radius", rad).putInt("opacity", op).putInt("blur", bl).putInt("dim", dm).apply()
            }.show()
    }
}
