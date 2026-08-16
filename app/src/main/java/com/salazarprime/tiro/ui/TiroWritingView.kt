package com.salazarprime.tiro.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import com.salazarprime.tiro.R
import kotlin.math.atan2
import kotlin.math.ceil

/** Draws the Tiro signature once, follows it with a pen, then leaves a typing caret. */
internal class TiroWritingView(
    context: Context,
    private val palette: TiroPalette,
    private val animateOnAttach: Boolean,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val word = resources.getString(R.string.app_name)
    private val textPath = Path()
    private val traceSegment = Path()
    private val penPosition = FloatArray(2)
    private val penTangent = FloatArray(2)
    private val penNibPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(7f * density, -3.5f * density)
        lineTo(7f * density, 3.5f * density)
        close()
    }
    private val wordBounds = RectF()
    private val contours = mutableListOf<MeasuredContour>()
    private var totalPathLength = 0f
    private var animationProgress = if (animateOnAttach) 0f else 1f
    private var animationConsumed = false
    private var animator: ValueAnimator? = null
    private var caretVisible = !animateOnAttach

    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = palette.aqua
        style = Paint.Style.FILL
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            124f,
            resources.displayMetrics,
        )
        typeface = Typeface.create(resources.getFont(R.font.dancing_script), 620, false)
    }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.aqua
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.65f * density
    }
    private val traceGlowPaint = Paint(tracePaint).apply {
        alpha = 46
        strokeWidth = 4.5f * density
    }
    private val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.amber
        style = Paint.Style.FILL
    }
    private val penBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.ink
        style = Paint.Style.FILL
    }
    private val penDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.deepGreen
        style = Paint.Style.FILL
    }
    private val penShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.canvas
        alpha = 92
        style = Paint.Style.FILL
    }

    private val startWriting = Runnable { beginWritingAnimation() }
    private val blinkCaret = object : Runnable {
        override fun run() {
            caretVisible = !caretVisible
            invalidate()
            postDelayed(this, TiroWritingTimeline.CARET_BLINK_MILLIS)
        }
    }

    init {
        contentDescription = "$word. Tap to replay the writing animation."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isFocusable = true
        setOnClickListener { replay() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rawPath = createRawTextPath()
        val rawBounds = RectF()
        rawPath.computeBounds(rawBounds, true)
        val desiredWidth = ceil(rawBounds.width() + 64f * density).toInt()
        val desiredHeight = ceil(rawBounds.height() + 34f * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildTextPath(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalPathLength <= 0f) return

        val writingProgress = TiroWritingTimeline.writingProgress(animationProgress)
        drawTrace(canvas, writingProgress)

        val fillAlpha = TiroWritingTimeline.fillAlpha(animationProgress)
        if (fillAlpha > 0f) {
            wordPaint.alpha = (fillAlpha * 255).toInt()
            canvas.drawPath(textPath, wordPaint)
        }

        val penAlpha = TiroWritingTimeline.penAlpha(animationProgress)
        if (penAlpha > 0f) drawPen(canvas, writingProgress, penAlpha)

        if (TiroWritingTimeline.caretReady(animationProgress) && caretVisible) {
            val caretY = (wordBounds.bottom + 3.5f * density)
                .coerceAtMost(height - 2f * density)
            canvas.drawRoundRect(
                wordBounds.right + 1.5f * density,
                caretY,
                wordBounds.right + 26.5f * density,
                caretY + 3f * density,
                1.5f * density,
                1.5f * density,
                amberPaint,
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animateOnAttach && !animationConsumed && ValueAnimator.areAnimatorsEnabled()) {
            postDelayed(startWriting, TiroWritingTimeline.START_DELAY_MILLIS)
        } else {
            showCompletedWordmark()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(startWriting)
        removeCallbacks(blinkCaret)
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun replay() {
        removeCallbacks(startWriting)
        removeCallbacks(blinkCaret)
        animator?.cancel()
        animator = null
        animationConsumed = false
        animationProgress = 0f
        caretVisible = false
        invalidate()
        if (ValueAnimator.areAnimatorsEnabled()) {
            postDelayed(startWriting, REPLAY_DELAY_MILLIS)
        } else {
            showCompletedWordmark()
        }
    }

    private fun beginWritingAnimation() {
        if (!isAttachedToWindow || animationConsumed) return
        animationConsumed = true
        caretVisible = false
        animationProgress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TiroWritingTimeline.DURATION_MILLIS
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                animationProgress = valueAnimator.animatedValue as Float
                invalidate()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    private var wasCancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        wasCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        animator = null
                        if (!wasCancelled) showCompletedWordmark()
                    }
                },
            )
            start()
        }
    }

    private fun showCompletedWordmark() {
        animationProgress = 1f
        caretVisible = true
        invalidate()
        removeCallbacks(blinkCaret)
        if (ValueAnimator.areAnimatorsEnabled()) {
            postDelayed(blinkCaret, TiroWritingTimeline.CARET_BLINK_MILLIS)
        }
    }

    private fun createRawTextPath(): Path = Path().also { path ->
        wordPaint.getTextPath(word, 0, word.length, 0f, 0f, path)
    }

    private fun rebuildTextPath(width: Int, height: Int) {
        val rawPath = createRawTextPath()
        val rawBounds = RectF()
        rawPath.computeBounds(rawBounds, true)
        val top = ((height - rawBounds.height()) / 2f + 4f * density)
            .coerceAtLeast(20f * density)
        val availableWidth = width - 64f * density
        val scale = if (rawBounds.width() > availableWidth && availableWidth > 0f) {
            availableWidth / rawBounds.width()
        } else {
            1f
        }
        val left = (width - rawBounds.width() * scale) / 2f
        val transform = Matrix().apply {
            setScale(scale, scale)
            postTranslate(left - rawBounds.left * scale, top - rawBounds.top * scale)
        }
        textPath.reset()
        rawPath.transform(transform, textPath)
        textPath.computeBounds(wordBounds, true)
        rebuildContours()
    }

    private fun rebuildContours() {
        contours.clear()
        totalPathLength = 0f
        val measure = PathMeasure(textPath, false)
        do {
            val length = measure.length
            if (length > 0f) {
                val contourPath = Path()
                measure.getSegment(0f, length, contourPath, true)
                contours += MeasuredContour(
                    measure = PathMeasure(contourPath, false),
                    length = length,
                )
                totalPathLength += length
            }
        } while (measure.nextContour())
    }

    private fun drawTrace(canvas: Canvas, writingProgress: Float) {
        var remaining = totalPathLength * writingProgress.coerceIn(0f, 1f)
        contours.forEach { contour ->
            if (remaining <= 0f) return@forEach
            val distance = remaining.coerceAtMost(contour.length)
            traceSegment.rewind()
            contour.measure.getSegment(0f, distance, traceSegment, true)
            canvas.drawPath(traceSegment, traceGlowPaint)
            canvas.drawPath(traceSegment, tracePaint)
            remaining -= contour.length
        }
    }

    private fun drawPen(canvas: Canvas, writingProgress: Float, alpha: Float) {
        val distance = (totalPathLength * writingProgress.coerceIn(0f, 1f))
            .coerceAtMost((totalPathLength - 0.01f).coerceAtLeast(0f))
        var contourStart = 0f
        val contour = contours.firstOrNull { measured ->
            if (distance <= contourStart + measured.length) {
                true
            } else {
                contourStart += measured.length
                false
            }
        } ?: contours.lastOrNull() ?: return

        val localDistance = (distance - contourStart).coerceIn(0f, contour.length)
        if (!contour.measure.getPosTan(localDistance, penPosition, penTangent)) return

        val tangentDegrees = Math.toDegrees(
            atan2(penTangent[1].toDouble(), penTangent[0].toDouble()),
        ).toFloat()
        val penAngle = (-48f + tangentDegrees * 0.08f).coerceIn(-58f, -36f)
        val visibleAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        penShadowPaint.alpha = (visibleAlpha * 0.36f).toInt()
        penBodyPaint.alpha = visibleAlpha
        penDetailPaint.alpha = visibleAlpha
        amberPaint.alpha = visibleAlpha

        canvas.save()
        canvas.translate(penPosition[0], penPosition[1])
        canvas.rotate(penAngle)
        canvas.drawRoundRect(
            7f * density,
            -1f * density,
            31f * density,
            6f * density,
            3.5f * density,
            3.5f * density,
            penShadowPaint,
        )
        canvas.drawRoundRect(
            6f * density,
            -3.5f * density,
            29f * density,
            3.5f * density,
            3.5f * density,
            3.5f * density,
            penBodyPaint,
        )
        canvas.drawRoundRect(
            21f * density,
            -3.5f * density,
            29f * density,
            3.5f * density,
            3.5f * density,
            3.5f * density,
            amberPaint,
        )
        canvas.drawRect(
            8f * density,
            -0.65f * density,
            22f * density,
            0.65f * density,
            penDetailPaint,
        )
        canvas.drawPath(penNibPath, amberPaint)
        canvas.drawCircle(5.2f * density, 0f, 0.9f * density, penDetailPaint)
        canvas.restore()

        amberPaint.alpha = 255
    }

    private data class MeasuredContour(
        val measure: PathMeasure,
        val length: Float,
    )
}

internal object TiroWritingTimeline {
    const val START_DELAY_MILLIS = 140L
    const val DURATION_MILLIS = 2_600L
    const val CARET_BLINK_MILLIS = 520L
    private const val WRITING_END = 0.84f
    private const val FILL_START = 0.72f
    private const val FILL_END = 0.94f
    private const val PEN_FADE_END = 0.94f

    fun writingProgress(progress: Float): Float =
        (progress.coerceIn(0f, 1f) / WRITING_END).coerceIn(0f, 1f)

    fun fillAlpha(progress: Float): Float =
        ((progress.coerceIn(0f, 1f) - FILL_START) / (FILL_END - FILL_START))
            .coerceIn(0f, 1f)

    fun penAlpha(progress: Float): Float = when {
        progress <= WRITING_END -> 1f
        progress >= PEN_FADE_END -> 0f
        else -> 1f - (progress - WRITING_END) / (PEN_FADE_END - WRITING_END)
    }

    fun caretReady(progress: Float): Boolean = progress >= FILL_END
}

private const val REPLAY_DELAY_MILLIS = 70L
