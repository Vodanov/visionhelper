package com.kzv.visionhelper

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var bounds = Rect()

    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        strokeWidth = 6F
        style = Paint.Style.STROKE
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 36f
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        textSize = 36f
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    fun clear() {
        results = listOf()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        results.forEach { box ->
            val left = box.x1 * width
            val top = box.y1 * height
            val right = box.x2 * width
            val bottom = box.y2 * height

            canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, boxPaint)

            val label = "${box.clsName} ${"%.2f".format(box.cnf)}"
            textBackgroundPaint.getTextBounds(label, 0, label.length, bounds)

            val textWidth = bounds.width().toFloat()
            val textHeight = bounds.height().toFloat()

            val textBackgroundRect = RectF(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING
            )
            canvas.drawRoundRect(textBackgroundRect, 8f, 8f, textBackgroundPaint)
            canvas.drawText(label, left, top + textHeight, textPaint)
        }
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8f
    }
}
