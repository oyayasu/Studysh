package com.oyayasu.studysh

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*

enum class MascotMood { Idle, Thinking, Happy, Sad, Celebrate }

/** Chibi reactions: anticipation, squash/stretch, little hops, paw gestures and expression changes. */
@Composable fun StudyshMascot(modifier: Modifier = Modifier, mood: MascotMood = MascotMood.Idle, eventKey: Int = 0) {
    val idle = rememberInfiniteTransition(label = "mascot idle")
    val breathe by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathing")
    val blink by idle.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 5200
        1f at 0; 1f at 3200; .06f at 3290; 1f at 3410
        1f at 3560; .08f at 3650; 1f at 3760; 1f at 5200
    }), label = "double blink")
    val reaction = remember { Animatable(0f) }
    LaunchedEffect(mood, eventKey) {
        reaction.snapTo(0f)
        if (mood != MascotMood.Idle) reaction.animateTo(1f, tween(
            if (mood == MascotMood.Celebrate) 1650 else 1000, easing = LinearEasing))
    }
    val happy = mood == MascotMood.Happy || mood == MascotMood.Celebrate
    val joy by animateFloatAsState(if (happy) 1f else 0f, spring(.5f, 350f), label = "smile")
    val shy by animateFloatAsState(if (mood == MascotMood.Sad) 1f else 0f, tween(220), label = "sheepish")
    val thinking by animateFloatAsState(if (mood == MascotMood.Thinking) 1f else 0f, tween(220), label = "curious")
    val phase = reaction.value
    val burst = sin(phase * PI).toFloat().coerceAtLeast(0f)
    val wobble = sin(phase * PI * 8).toFloat() * (1f - phase)
    val jump = if (happy && phase in .12f.. .85f)
        sin((phase - .12f) / .73f * PI * if (mood == MascotMood.Celebrate) 2 else 1).toFloat().let { abs(it) } else 0f
    val anticipation = if (happy && phase < .12f) sin(phase / .12f * PI).toFloat() else 0f
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val ink = Color(0xFF493555)
        drawOval(Color(0xFF9B79BA).copy(alpha = .13f - .06f * jump), Offset(w * (.23f + .03f * jump), h * .9f),
            Size(w * (.54f - .06f * jump), h * .055f))
        if (joy > .1f && phase < 1f) {
            repeat(if (mood == MascotMood.Celebrate) 9 else 5) { i ->
                val angle = (-165 + i * 31).toFloat() * PI.toFloat() / 180f
                val radius = w * (.26f + .22f * phase)
                val center = Offset(w * .5f + cos(angle) * radius, h * .48f + sin(angle) * radius - h * .06f * phase)
                val r = w * .033f * burst
                val star = Path().apply {
                    moveTo(center.x, center.y - r * 1.5f)
                    lineTo(center.x + r * .35f, center.y - r * .35f); lineTo(center.x + r, center.y)
                    lineTo(center.x + r * .35f, center.y + r * .35f); lineTo(center.x, center.y + r * 1.5f)
                    lineTo(center.x - r * .35f, center.y + r * .35f); lineTo(center.x - r, center.y)
                    lineTo(center.x - r * .35f, center.y - r * .35f); close()
                }
                drawPath(star, (if (i % 2 == 0) Color(0xFFFFCF74) else Color(0xFFEEA5CD)).copy(alpha = (1f - phase).coerceIn(0f, 1f)))
            }
        }
        withTransform({
            translate(0f, -h * (.018f * breathe + .095f * jump) + h * .02f * shy)
            rotate(thinking * 5f + joy * wobble * 7f + shy * wobble * 9f, Offset(w * .5f, h * .72f))
            scale(1f + .018f * breathe + .11f * anticipation - .045f * jump,
                1f - .018f * breathe - .10f * anticipation + .07f * jump - .035f * shy, Offset(w * .5f, h * .8f))
        }) {
            // Tiny feet and animated paws make the reaction read as a character, not a floating icon.
            drawOval(Color(0xFFA77ACC), Offset(w * .26f, h * .83f), Size(w * .17f, h * .09f))
            drawOval(Color(0xFFA77ACC), Offset(w * .57f, h * .83f), Size(w * .17f, h * .09f))
            drawOval(Brush.radialGradient(listOf(Color(0xFFF9F0FF), Color(0xFFDCC2F1), Color(0xFFAF83D1)),
                center = Offset(w * .36f, h * .23f), radius = w * .76f), Offset(w * .13f, h * .16f), Size(w * .74f, h * .71f))
            val tuft = Path().apply {
                moveTo(w * .39f, h * .2f)
                cubicTo(w * .36f, h * .03f, w * .50f, h * .09f, w * .50f, h * .16f)
                cubicTo(w * .54f, h * .06f, w * .64f, h * .10f, w * .59f, h * .21f)
            }
            drawPath(tuft, Color(0xFFE4CDF5))
            drawOval(Color.White.copy(alpha = .4f), Offset(w * .28f, h * .23f), Size(w * .3f, h * .09f))
            listOf(.13f, .79f).forEachIndexed { index, x ->
                rotate((if (index == 0) -1f else 1f) * (15f + joy * (35f + 20f * wobble) + thinking * 12f), Offset(w * (x + .04f), h * .64f)) {
                    drawOval(Color(0xFFD6B4EF), Offset(w * x, h * (.6f - .035f * joy)), Size(w * .10f, h * .16f))
                }
            }
            val eyeY = h * (.49f + .018f * shy)
            listOf(.36f, .59f).forEach { x ->
                if (joy > .6f) {
                    drawArc(ink, 195f, 150f, false, Offset(w * x, eyeY - h * .015f), Size(w * .075f, h * .07f), style = Stroke(w * .019f, cap = StrokeCap.Round))
                } else {
                    val eyeHeight = h * (.095f + .02f * thinking) * blink
                    drawOval(ink, Offset(w * x, eyeY - eyeHeight * .5f), Size(w * .061f, eyeHeight.coerceAtLeast(1f)))
                    if (blink > .5f) drawCircle(Color.White.copy(alpha = .9f), w * .012f, Offset(w * (x + .019f), eyeY - eyeHeight * .16f))
                }
            }
            listOf(.27f, .66f).forEach { x ->
                drawOval(Color(0xFFF39BC1).copy(alpha = .30f + .32f * joy + .14f * shy), Offset(w * x, h * .57f), Size(w * .105f, h * .052f))
                repeat(2) { stripe -> drawLine(Color(0xFFE88DB9).copy(alpha = .5f), Offset(w * (x + .028f + .028f * stripe), h * .582f),
                    Offset(w * (x + .018f + .028f * stripe), h * .607f), w * .006f, StrokeCap.Round) }
            }
            val mouth = Path().apply {
                moveTo(w * .445f, h * .625f)
                cubicTo(w * .455f, h * (.67f + .025f * joy), w * .49f, h * .68f, w * .505f, h * .64f)
                cubicTo(w * .52f, h * .68f, w * .555f, h * (.67f + .025f * joy), w * .565f, h * .625f)
            }
            drawPath(mouth, ink, style = Stroke(w * .017f, cap = StrokeCap.Round))
            if (shy > .05f) {
                // A small, playful sweat drop instead of a punitive crying reaction.
                val dropY = h * (.36f + .07f * phase)
                val drop = Path().apply {
                    moveTo(w * .76f, dropY - h * .04f)
                    cubicTo(w * .70f, dropY + h * .03f, w * .78f, dropY + h * .08f, w * .80f, dropY + h * .02f)
                    quadraticBezierTo(w * .80f, dropY, w * .76f, dropY - h * .04f)
                }
                drawPath(drop, Color(0xFF91C9ED).copy(alpha = shy * .9f))
            }
        }
    }
}
