package com.morningboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 코드로 뷰 만들 때 쓰는 작은 도우미 */
object C {
    const val BG = 0xFF0F1115.toInt()
    const val CARD = 0xFF1A1E26.toInt()
    const val CARD2 = 0xFF242A35.toInt()
    /** 날씨 배경이 살짝 비치는 반투명 카드 */
    const val GLASS = 0xB8161A22.toInt()
    const val GLASS2 = 0xCC222833.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val SUB = 0xFFBAC0CC.toInt()
    const val DIM = 0xFF868E9C.toInt()
    const val ACCENT = 0xFFFFD43B.toInt()
    const val BLUE = 0xFF339AF0.toInt()
    const val RED = 0xFFFF6B6B.toInt()
}

fun Context.dp(v: Number): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun rounded(color: Int, radiusPx: Float) = GradientDrawable().apply {
    setColor(color); cornerRadius = radiusPx
}

fun Context.text(s: String = "", sp: Float = 16f, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
}

fun Context.button(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    isAllCaps = false
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    setOnClickListener { onClick() }
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)

fun View.margins(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): View {
    val p = layoutParams as? ViewGroup.MarginLayoutParams ?: lp()
    p.setMargins(l, t, r, b)
    layoutParams = p
    return this
}
