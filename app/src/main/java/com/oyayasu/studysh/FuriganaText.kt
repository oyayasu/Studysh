package com.oyayasu.studysh

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

data class FuriganaSettings(val enabled: Boolean = false, val japanese: Boolean = false,
    val overrides: Map<String, String> = emptyMap())
val LocalFurigana = staticCompositionLocalOf { FuriganaSettings() }

/** A real native ruby span: the reading reserves space ABOVE its base text. */
class FuriganaSpan(private val reading: String) : ReplacementSpan() {
    private fun rubyPaint(paint: Paint) = Paint(paint).apply { textSize *= .55f; typeface = Typeface.DEFAULT }
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val ruby = rubyPaint(paint)
        val base = paint.fontMetricsInt
        if (fm != null) {
            fm.ascent = base.ascent + ruby.fontMetricsInt.ascent - 2
            fm.top = fm.ascent
            fm.descent = base.descent
            fm.bottom = base.bottom
            fm.leading = base.leading
        }
        return ceil(max(paint.measureText(text, start, end), ruby.measureText(reading)) + 3f).toInt()
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int,
        y: Int, bottom: Int, paint: Paint) {
        val ruby = rubyPaint(paint)
        val width = getSize(paint, text, start, end, null).toFloat()
        canvas.drawText(text, start, end, x + (width - paint.measureText(text, start, end)) / 2f, y.toFloat(), paint)
        canvas.drawText(reading, x + (width - ruby.measureText(reading)) / 2f,
            y + paint.fontMetrics.ascent - ruby.fontMetrics.descent - 2f, ruby)
    }
}

@Composable fun StudyText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current, selectable: Boolean = false,
    onStudy: ((String, String) -> Unit)? = null) =
    StudyText(AnnotatedString(text), modifier, color, fontSize, fontWeight, fontStyle, lineHeight,
        maxLines, textAlign, style, selectable, onStudy)

@Composable fun StudyText(text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current, selectable: Boolean = false,
    onStudy: ((String, String) -> Unit)? = null) {
    val settings = LocalFurigana.current
    val resolved = style.merge(TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight,
        fontStyle = fontStyle, lineHeight = lineHeight, textAlign = textAlign ?: TextAlign.Unspecified))
    if (!settings.enabled || !settings.japanese || !JapaneseReadings.hasKanji(text.text)) {
        Text(text, modifier, style = resolved, maxLines = maxLines)
        return
    }
    val annotation by produceState(text.text to listOf(RubySegment(text.text)), text.text, settings.overrides) {
        value = text.text to withContext(Dispatchers.Default) {
            runCatching { JapaneseReadings.annotate(text.text, settings.overrides) }
                .getOrElse { listOf(RubySegment(text.text)) }
        }
    }
    val segments = if (annotation.first == text.text) annotation.second else listOf(RubySegment(text.text))
    val base = segments.joinToString("") { it.text }
    val density = LocalDensity.current
    val pixels = with(density) { (resolved.fontSize.takeUnless { it == TextUnit.Unspecified } ?: 16.sp).toPx() }
    val height = with(density) { (resolved.lineHeight.takeUnless { it == TextUnit.Unspecified } ?: 24.sp).toPx() }
    val ink = if (resolved.color == Color.Unspecified) LocalContentColor.current else resolved.color
    val nativeStyle = (if ((resolved.fontWeight?.weight ?: 400) >= 600) Typeface.BOLD else 0) or
        (if (resolved.fontStyle == FontStyle.Italic) Typeface.ITALIC else 0)
    val content = remember(segments, text) {
        SpannableString(base).apply {
            if (base == text.text) text.spanStyles.forEach { span ->
                val face = (if ((span.item.fontWeight?.weight ?: 400) >= 600) Typeface.BOLD else 0) or
                    (if (span.item.fontStyle == FontStyle.Italic) Typeface.ITALIC else 0)
                if (face != 0) setSpan(StyleSpan(face), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            var offset = 0
            segments.forEach { segment ->
                segment.reading?.let { setSpan(FuriganaSpan(it), offset, offset + segment.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                offset += segment.text.length
            }
        }
    }
    AndroidView(modifier = modifier, factory = { context ->
        TextView(context).apply { includeFontPadding = false; setPadding(0, 0, 0, 0) }
    }, update = { view ->
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels)
        view.setTextColor(ink.toArgb())
        view.typeface = Typeface.create(Typeface.DEFAULT, nativeStyle)
        view.setLineSpacing((height - view.paint.fontSpacing).coerceAtLeast(0f), 1f)
        view.maxLines = maxLines
        view.ellipsize = if (maxLines != Int.MAX_VALUE) TextUtils.TruncateAt.END else null
        view.textAlignment = if (textAlign == TextAlign.Center) TextView.TEXT_ALIGNMENT_CENTER else TextView.TEXT_ALIGNMENT_VIEW_START
        view.setTextIsSelectable(selectable)
        if (view.text != content) view.text = content
        view.customSelectionActionModeCallback = if (onStudy == null) null else object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, 4567, Menu.NONE, "Create prompt")
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId != 4567) return false
                val start = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
                val end = maxOf(view.selectionStart, view.selectionEnd).coerceIn(start, base.length)
                base.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { onStudy(it, base) }
                mode.finish()
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }
    })
}
