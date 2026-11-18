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

/** Native vector mascot: breathing idle -> looping celebration or soft anime crying.
 * Reactions are composed through eased weights rather than switching between image frames.
 */
@Composable fun StudyshMascot(modifier: Modifier = Modifier, mood: MascotMood = MascotMood.Idle, eventKey: Int = 0) {
    val idle = rememberInfiniteTransition(label = "mascot idle")
    val breathe by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathing")
    val blink by idle.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 5200
        1f at 0; 1f at 3200; .06f at 3290; 1f at 3410
        1f at 3560; .08f at 3650; 1f at 3760; 1f at 5200
    }), label = "double blink")
    // Everything is drawn as resolution-independent vectors, not GIF frames.
    // The reaction weights ease in and back out; the two cycles have seamless endpoints.
    val happy = mood == MascotMood.Happy || mood == MascotMood.Celebrate
    val joy by animateFloatAsState(
        if (happy) 1f else 0f, tween(520, easing = FastOutSlowInEasing), label = "dance transition")
    val sad by animateFloatAsState(
        if (mood == MascotMood.Sad) 1f else 0f, tween(620, easing = FastOutSlowInEasing), label = "cry transition")
    val thinking by animateFloatAsState(
        if (mood == MascotMood.Thinking) 1f else 0f, tween(240), label = "curious")
    val phase by idle.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1650, easing = LinearEasing)), label = "dance loop")
    val tearPhase by idle.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "tear loop")
    val danceWave = sin(phase * 2f * PI).toFloat()
    val footWave = sin(phase * 4f * PI).toFloat()
    val sobWave = sin(tearPhase * 4f * PI).toFloat()
    val jump = joy * abs(footWave)
    val anticipation = joy * (1f - abs(footWave)) * .15f
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val ink = Color(0xFF493555)
        drawOval(Color(0xFF9B79BA).copy(alpha = .13f - .06f * jump), Offset(w * (.23f + .03f * jump), h * .9f),
            Size(w * (.54f - .06f * jump), h * .055f))
        if (joy > .01f) {
            repeat(if (mood == MascotMood.Celebrate) 9 else 6) { i ->
                val wave = (.5f + .5f * sin(phase * 2f * PI + i * .9f)).toFloat()
                val angle = (-165 + i * 32).toFloat() * PI.toFloat() / 180f
                val radius = w * (.32f + .04f * wave)
                val center = Offset(w * .5f + cos(angle) * radius, h * .47f + sin(angle) * radius)
                val r = w * .022f * wave
                val star = Path().apply {
                    moveTo(center.x, center.y - r * 1.5f)
                    lineTo(center.x + r * .35f, center.y - r * .35f); lineTo(center.x + r, center.y)
                    lineTo(center.x + r * .35f, center.y + r * .35f); lineTo(center.x, center.y + r * 1.5f)
                    lineTo(center.x - r * .35f, center.y + r * .35f); lineTo(center.x - r, center.y)
                    lineTo(center.x - r * .35f, center.y - r * .35f); close()
                }
                drawPath(star, (if (i % 2 == 0) Color(0xFFFFCF74) else Color(0xFFEEA5CD))
                    .copy(alpha = joy * wave))
            }
        }
        withTransform({
            translate(w * (.025f * joy * danceWave + .004f * sad * sobWave),
                -h * (.018f * breathe + .080f * jump) + h * .020f * sad)
            rotate(thinking * 5f + joy * danceWave * 9f + sad * sobWave * 2f,
                Offset(w * .5f, h * .72f))
            scale(1f + .018f * breathe + .11f * anticipation - .035f * jump,
                1f - .018f * breathe - .10f * anticipation + .07f * jump - .035f * sad,
                Offset(w * .5f, h * .8f))
        }) {
            // Tiny feet and animated paws make the reaction read as a character, not a floating icon.
            val leftStep = joy * max(0f, danceWave)
            val rightStep = joy * max(0f, -danceWave)
            drawOval(Color(0xFFA77ACC), Offset(w * (.26f + .018f * joy * danceWave),
                h * (.83f - .06f * leftStep)), Size(w * .17f, h * .09f))
            drawOval(Color(0xFFA77ACC), Offset(w * (.57f + .018f * joy * danceWave),
                h * (.83f - .06f * rightStep)), Size(w * .17f, h * .09f))
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
                rotate((if (index == 0) -1f else 1f) *
                    (15f + joy * (38f + 18f * danceWave) + thinking * 12f + sad * 12f),
                    Offset(w * (x + .04f), h * .64f)) {
                    drawOval(Color(0xFFD6B4EF), Offset(w * (x + (if (index == 0) .03f else -.03f) * sad),
                        h * (.6f - .035f * joy - .025f * sad)), Size(w * .10f, h * .16f))
                }
            }
            val eyeY = h * (.49f + .018f * sad)
            listOf(.36f, .59f).forEachIndexed { index, x ->
                val eyeHeight = h * (.095f + .023f * thinking + .025f * sad) *
                    (blink * (1f - sad) + sad)
                val openAlpha = (1f - joy).coerceIn(0f, 1f)
                if (openAlpha > .001f) {
                    drawOval(ink.copy(alpha = openAlpha), Offset(w * x, eyeY - eyeHeight * .5f),
                        Size(w * .061f, eyeHeight.coerceAtLeast(1f)))
                    drawCircle(Color.White.copy(alpha = .94f * openAlpha), w * (.012f + .004f * sad),
                        Offset(w * (x + .019f), eyeY - eyeHeight * .16f))
                    if (sad > .01f) {
                        drawCircle(Color.White.copy(alpha = .87f * sad * openAlpha), w * .007f,
                            Offset(w * (x + .041f), eyeY + eyeHeight * .20f))
                        drawArc(ink.copy(alpha = sad * openAlpha), 195f, 145f, false,
                            Offset(w * (x + if (index == 0) -.01f else .004f), eyeY - h * .108f),
                            Size(w * .072f, h * .044f), style = Stroke(w * .013f, cap = StrokeCap.Round))
                    }
                }
                if (joy > .001f) drawArc(ink.copy(alpha = joy), 195f, 150f, false,
                    Offset(w * x, eyeY - h * .015f), Size(w * .075f, h * .07f),
                    style = Stroke(w * .019f, cap = StrokeCap.Round))
            }
            listOf(.27f, .66f).forEach { x ->
                drawOval(Color(0xFFF39BC1).copy(alpha = .30f + .32f * joy + .18f * sad), Offset(w * x, h * .57f), Size(w * .105f, h * .052f))
                repeat(2) { stripe -> drawLine(Color(0xFFE88DB9).copy(alpha = .5f), Offset(w * (x + .028f + .028f * stripe), h * .582f),
                    Offset(w * (x + .018f + .028f * stripe), h * .607f), w * .006f, StrokeCap.Round) }
            }
            val mouth = Path().apply {
                moveTo(w * .445f, h * .625f)
                cubicTo(w * .455f, h * (.67f + .025f * joy), w * .49f, h * .68f, w * .505f, h * .64f)
                cubicTo(w * .52f, h * .68f, w * .555f, h * (.67f + .025f * joy), w * .565f, h * .625f)
            }
            drawPath(mouth, ink.copy(alpha = 1f - sad), style = Stroke(w * .017f, cap = StrokeCap.Round))
            if (sad > .001f) {
                // The idle "uwu" mouth gently morphs into a trembling anime pout.
                drawOval(ink.copy(alpha = sad), Offset(w * .473f,
                    h * (.626f + .003f * sobWave)), Size(w * .065f, h * .057f))
                drawOval(Color(0xFFFFB2C4).copy(alpha = sad), Offset(w * .484f,
                    h * (.657f + .003f * sobWave)), Size(w * .044f, h * .020f))

                // Continuous, offset tear streams and falling droplets, all fading in/out.
                listOf(.36f, .59f).forEachIndexed { index, x ->
                    val tearX = w * (x + .027f)
                    val tearStart = h * (.544f + .017f * sad)
                    val tearEnd = h * (.58f + .065f * sad + .012f * abs(sobWave))
                    drawLine(Color(0xFF83C6F3).copy(alpha = sad * .88f),
                        Offset(tearX, tearStart), Offset(tearX, tearEnd),
                        w * .019f * sad, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = sad * .7f),
                        Offset(tearX - w * .006f, tearStart), Offset(tearX - w * .006f, tearEnd),
                        w * .005f * sad, StrokeCap.Round)
                    drawOval(Color(0xFFB2E5FF).copy(alpha = sad),
                        Offset(tearX - w * .030f, tearStart - h * .007f),
                        Size(w * .059f, h * .028f))
                    repeat(2) { drop ->
                        val p = (tearPhase + drop * .5f + index * .17f) % 1f
                        val alpha = sad * (1f - p)
                        val y = h * (.60f + .25f * p)
                        drawOval(Color(0xFF86CDF9).copy(alpha = alpha),
                            Offset(tearX - w * .011f + (if (index == 0) -1f else 1f) * w * .055f * p, y),
                            Size(w * (.021f + .014f * (1f - p)), h * .040f))
                        drawCircle(Color.White.copy(alpha = alpha * .9f), w * .007f * (1f - .4f * p),
                            Offset(tearX + w * .002f + (if (index == 0) -1f else 1f) * w * .055f * p,
                                y + h * .010f))
                    }
                }
            }
        }
    }
}
