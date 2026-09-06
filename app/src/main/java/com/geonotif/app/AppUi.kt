package com.geonotif.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import android.view.View
import com.google.android.material.color.MaterialColors
import android.widget.LinearLayout

/** Material 3 components with a shared travel-inspired color palette. */
object AppUi {
    // The branded journey card intentionally stays dark in both system themes.
    val ink = Color.rgb(21, 43, 51)
    val mint = Color.rgb(185, 242, 217)

    data class Palette(
        val background: Int, val ink: Int, val muted: Int, val teal: Int,
        val pale: Int, val border: Int, val danger: Int, val dangerContainer: Int,
        val surface: Int, val onPrimary: Int
    )

    fun palette(context: Context): Palette {
        fun color(attr: Int) = MaterialColors.getColor(context, attr, "GeoNotif theme")
        return Palette(
            color(com.google.android.material.R.attr.colorSurface),
            color(com.google.android.material.R.attr.colorOnSurface),
            color(com.google.android.material.R.attr.colorOnSurfaceVariant),
            color(com.google.android.material.R.attr.colorPrimary),
            color(com.google.android.material.R.attr.colorSecondaryContainer),
            color(com.google.android.material.R.attr.colorOutlineVariant),
            color(com.google.android.material.R.attr.colorError),
            color(com.google.android.material.R.attr.colorErrorContainer),
            color(com.google.android.material.R.attr.colorSurfaceContainerLow),
            color(com.google.android.material.R.attr.colorOnPrimary)
        )
    }

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun shape(context: Context, color: Int, radius: Int = 20, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radius).toFloat()
        stroke?.let { setStroke(dp(context, 1), it) }
    }
    fun label(context: Context, value: String, size: Float = 16f, bold: Boolean = false, color: Int = palette(context).ink) = MaterialTextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(context, 2).toFloat(), 1f)
        setPadding(0, dp(context, 4), 0, dp(context, 4))
    }
    fun button(context: Context, label: String, primary: Boolean = false, onClick: () -> Unit): MaterialButton {
        val colors = palette(context)
        val style = if (primary) com.google.android.material.R.style.Widget_Material3_Button
            else com.google.android.material.R.style.Widget_Material3_Button_TonalButton
        return MaterialButton(ContextThemeWrapper(context, style), null, 0).apply {
            text = label; textSize = 15f; isAllCaps = false
            minHeight = dp(context, 52); minimumWidth = 0
            insetTop = 0; insetBottom = 0
            cornerRadius = dp(context, 26)
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            backgroundTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(colors.border, if (primary) colors.teal else colors.pale))
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(colors.muted, if (primary) colors.onPrimary else colors.ink)))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, 6); bottomMargin = dp(context, 6)
            }
            setOnClickListener { onClick() }
        }
    }

    fun surface(context: Context, body: View, dark: Boolean = false) = MaterialCardView(context).apply {
        radius = dp(context, 24).toFloat()
        cardElevation = 0f
        strokeWidth = if (dark) 0 else dp(context, 1)
        val colors = palette(context)
        strokeColor = colors.border
        setCardBackgroundColor(if (dark) ink else colors.surface)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 16) }
        addView(body)
    }
}

/** Decorative route illustration, deliberately excluded from accessibility navigation. */
class JourneyArt(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    private val route = Path().apply {
        moveTo(13f, 77f); cubicTo(65f, 91f, 21f, 28f, 66f, 32f)
        cubicTo(86f, 34f, 71f, 66f, 107f, 51f)
    }
    private val pin = Path().apply {
        moveTo(99f, 54f); lineTo(110f, 73f); lineTo(121f, 54f); close()
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 150f, height / 100f)
        canvas.save()
        canvas.translate((width - 150 * scale) / 2, (height - 100 * scale) / 2)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(39, 70, 76)
        canvas.drawCircle(103f, 42f, 42f, paint)
        paint.color = AppUi.mint
        paint.alpha = 95
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
        paint.pathEffect = dash
        canvas.drawPath(route, paint)
        paint.pathEffect = null; paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawCircle(13f, 77f, 5f, paint)
        canvas.drawCircle(66f, 32f, 4f, paint)
        paint.color = AppUi.mint
        canvas.drawCircle(110f, 47f, 14f, paint)
        canvas.drawPath(pin, paint)
        paint.color = AppUi.ink
        canvas.drawCircle(110f, 46f, 5f, paint)
        canvas.restore()
    }
}
