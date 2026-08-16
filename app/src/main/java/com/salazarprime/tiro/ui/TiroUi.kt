package com.salazarprime.tiro.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

internal data class TiroPalette(
    val canvas: Int,
    val surface: Int,
    val field: Int,
    val ink: Int,
    val stroke: Int,
    val coral: Int,
    val aqua: Int,
    val deepGreen: Int,
    val amber: Int,
)

internal fun Context.tiroPalette(): TiroPalette = TiroPalette(
    canvas = getColor(com.salazarprime.tiro.R.color.tiro_canvas),
    surface = getColor(com.salazarprime.tiro.R.color.tiro_surface),
    field = getColor(com.salazarprime.tiro.R.color.tiro_field),
    ink = getColor(com.salazarprime.tiro.R.color.tiro_ink),
    stroke = getColor(com.salazarprime.tiro.R.color.tiro_stroke),
    coral = getColor(com.salazarprime.tiro.R.color.tiro_coral),
    aqua = getColor(com.salazarprime.tiro.R.color.tiro_aqua),
    deepGreen = getColor(com.salazarprime.tiro.R.color.tiro_deep_green),
    amber = getColor(com.salazarprime.tiro.R.color.tiro_amber),
)

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun roundedBackground(
    fill: Int,
    radius: Float,
    strokeColor: Int? = null,
    strokeWidth: Int = 0,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(fill)
    cornerRadius = radius
    if (strokeColor != null && strokeWidth > 0) {
        setStroke(strokeWidth, strokeColor)
    }
}

internal fun Context.glassBackground(
    radius: Float,
    strokeColor: Int = tiroPalette().aqua.withOpacity(0.20f),
    strokeWidth: Int = dp(1),
): LayerDrawable {
    val palette = tiroPalette()
    val base = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(palette.stroke, palette.surface, palette.deepGreen),
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
    }
    val cornerGlow = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        colors = intArrayOf(palette.aqua.withOpacity(0.17f), Color.TRANSPARENT)
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = dp(230).toFloat()
        setGradientCenter(0.14f, 0.04f)
        cornerRadius = radius
    }
    val rim = roundedBackground(
        fill = Color.TRANSPARENT,
        radius = radius,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
    )
    return LayerDrawable(arrayOf(base, cornerGlow, rim))
}

internal fun rippleBackground(
    fill: Int,
    radius: Float,
    ripple: Int,
    strokeColor: Int? = null,
    strokeWidth: Int = 0,
): RippleDrawable = RippleDrawable(
    ColorStateList.valueOf(ripple),
    roundedBackground(fill, radius, strokeColor, strokeWidth),
    roundedBackground(Color.WHITE, radius),
)

internal fun Context.label(text: String, color: Int = tiroPalette().ink): TextView =
    TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = 11f
        typeface = Typeface.create("monospace", Typeface.BOLD)
        letterSpacing = 0.08f
        isAllCaps = true
    }

internal fun Context.actionButton(
    text: String,
    fill: Int,
    foreground: Int,
    contentDescription: String = text,
): Button = Button(this).apply {
    this.text = text
    this.contentDescription = contentDescription
    setTextColor(foreground)
    textSize = 13f
    typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    isAllCaps = false
    minHeight = dp(48)
    minimumHeight = dp(48)
    gravity = Gravity.CENTER
    setPadding(dp(16), 0, dp(16), 0)
    background = rippleBackground(
        fill = fill,
        radius = dp(14).toFloat(),
        ripple = foreground and 0x33FFFFFF,
    )
}

internal fun View.setMargins(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.setMargins(left, top, right, bottom)
    layoutParams = params
}

private fun Int.withOpacity(opacity: Float): Int {
    val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
    return (this and 0x00FFFFFF) or (alpha shl 24)
}
