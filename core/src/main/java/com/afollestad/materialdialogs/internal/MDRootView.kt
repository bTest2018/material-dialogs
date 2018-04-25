package com.afollestad.materialdialogs.internal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.Paint.Style.STROKE
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.afollestad.materialdialogs.R
import com.afollestad.materialdialogs.Theme
import com.afollestad.materialdialogs.extensions.dimenPx
import com.afollestad.materialdialogs.extensions.isVisible
import com.afollestad.materialdialogs.extensions.updatePadding

/**
 * Handles all measurement and positioning of the dialog and its views. Helps us inflate quickly,
 * adapt to scenarios quickly, and get around weird dialog height behavior which would occur
 * if we used straight-up stock views only.
 *
 * @author Aidan Follestad (afollestad)
 */
internal class MDRootView(
  context: Context,
  attrs: AttributeSet?
) : FrameLayout(context, attrs) {

  var maxHeight: Int = 0

  internal val dialogFrameMargin = dimenPx(R.dimen.md_dialog_frame_margin)
  internal val dialogFrameMarginHalf = dimenPx(R.dimen.md_dialog_frame_margin_half)

  internal val titleFrameMarginBottom = dimenPx(R.dimen.md_title_frame_margin_bottom)
  internal val buttonHeightDefault = dimenPx(R.dimen.md_action_button_height)
  internal val buttonHeightStacked = dimenPx(R.dimen.md_stacked_action_button_height)
  internal val buttonFramePadding = dimenPx(R.dimen.md_action_button_frame_padding)
  internal val buttonSpacing = dimenPx(R.dimen.md_action_button_spacing)

  var debugMode: Boolean = false
    set(value) {
      field = value
      invalidate()
    }
  var theme: Theme = Theme.LIGHT
    set(value) {
      field = value
      requestLayout()
    }
  internal var stackButtons: Boolean = false
    set(value) {
      field = value
      requestLayout()
    }

  internal var debugPaint: Paint? = null
  internal val dividerPaint: Paint
  internal var showTopDivider = false
  internal var showBottomDivider = false

  internal lateinit var frameMain: ViewGroup
  internal lateinit var titleView: TextView
  internal lateinit var actionButtons: Array<MDActionButton>

  init {
    setWillNotDraw(false)
    dividerPaint = Paint()
    dividerPaint.style = STROKE
    dividerPaint.strokeWidth = context.resources.getDimension(R.dimen.md_divider_height)
    dividerPaint.isAntiAlias = true
  }

  internal fun invalidateDividers(
    scrolledDown: Boolean,
    atBottom: Boolean
  ) {
    showTopDivider = scrolledDown
    showBottomDivider = atBottom
    invalidate()
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    frameMain = findViewById(R.id.md_frame_main)
    titleView = findViewById(R.id.md_text_title)
    actionButtons = arrayOf(
        findViewById(R.id.md_button_positive),
        findViewById(R.id.md_button_negative),
        findViewById(R.id.md_button_neutral)
    )
  }

  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int
  ) {
    val contentBottomPadding = getContentBottomPadding()
    if (contentBottomPadding > 0) {
      val contentView = frameMain.getChildAt(1)
      contentView.updatePadding(bottom = contentBottomPadding)
    }

    val specWidth = MeasureSpec.getSize(widthMeasureSpec)
    var specHeight = MeasureSpec.getSize(heightMeasureSpec)
    if (specHeight > maxHeight) {
      specHeight = maxHeight
    }

    measureButtons(specWidth)
    val resultHeight = measureMain(specWidth, specHeight)
    setMeasuredDimension(specWidth, resultHeight)
  }

  override fun onLayout(
    changed: Boolean,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
  ) {
    // Buttons
    val buttonFrameHeight = getTotalButtonFrameHeight()
    if (stackButtons) {
      var topY = measuredHeight - buttonFrameHeight
      for (button in getVisibleButtons()) {
        val bottomY = topY + buttonHeightStacked
        button.layout(0, topY, measuredWidth, bottomY)
        topY = bottomY
      }
    } else {
      var rightX = measuredWidth - buttonFramePadding
      val topY = measuredHeight - (buttonFrameHeight - buttonFramePadding)
      val bottomY = measuredHeight - buttonFramePadding
      for (button in getVisibleButtons()) {
        val leftX = rightX - button.measuredWidth
        button.layout(leftX, topY, rightX, bottomY)
        rightX = leftX - buttonSpacing
      }
    }
    // Main frame
    val frameButtonsTop = (measuredHeight - buttonFrameHeight)
    val mainFrameTop = getMainFrameTopPadding()
    frameMain.layout(0, mainFrameTop, measuredWidth, frameButtonsTop)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val buttonFrameHeight = getTotalButtonFrameHeight()
    val mainFrameTop = getMainFrameTopPadding()
    dividerPaint.color = getDividerColor()

    if (showTopDivider && titleView.isVisible()) {
      val topDividerY = (titleView.bottom + titleFrameMarginBottom + mainFrameTop).toFloat()
      canvas.drawLine(0f, topDividerY, measuredWidth.toFloat(), topDividerY, dividerPaint)
    }
    if (showBottomDivider && buttonFrameHeight > 0) {
      val bottomDividerY = (measuredHeight - buttonFrameHeight).toFloat()
      canvas.drawLine(0f, bottomDividerY, measuredWidth.toFloat(), bottomDividerY, dividerPaint)
    }

    if (!debugMode) {
      return
    }

    val debugColorTeal = Color.parseColor("#B4F9FA")
    val debugColorPink = Color.parseColor("#E79ACA")

    if (debugPaint == null) {
      debugPaint = Paint()
      debugPaint!!.strokeWidth = 5f
      debugPaint!!.style = Style.FILL_AND_STROKE
      debugPaint!!.isAntiAlias = true
    }

    // Main frame fill
    debugPaint!!.color = debugColorTeal
    canvas.drawRect(
        0f, 0f,
        measuredWidth.toFloat(),
        measuredHeight - buttonFrameHeight.toFloat(),
        debugPaint!!
    )

    // Hollow title frame fill
    debugPaint!!.color = Color.WHITE
    var contentTopY: Int = mainFrameTop
    if (titleView.isVisible()) {
      contentTopY = mainFrameTop + titleView.measuredHeight + titleFrameMarginBottom
      canvas.drawRect(
          dialogFrameMargin.toFloat(),
          mainFrameTop.toFloat(),
          measuredWidth - dialogFrameMargin.toFloat(),
          contentTopY.toFloat() - titleFrameMarginBottom,
          debugPaint!!
      )
    }

    // Hollow content frame fill
    val contentHollowStopY = measuredHeight - buttonFrameHeight - mainFrameTop
    canvas.drawRect(
        dialogFrameMargin.toFloat(),
        contentTopY.toFloat(),
        measuredWidth - dialogFrameMargin.toFloat(),
        contentHollowStopY.toFloat(),
        debugPaint!!
    )

    // Button frame fill
    if (buttonFrameHeight > 0) {
      debugPaint!!.color = debugColorPink
      canvas.drawRect(
          0f,
          measuredHeight - buttonFrameHeight.toFloat(),
          measuredWidth.toFloat(),
          measuredHeight.toFloat(),
          debugPaint!!
      )

      if (stackButtons) {
        // Button fills
        debugPaint!!.color = Color.WHITE
        debugPaint!!.style = Style.FILL_AND_STROKE
        for (button in getVisibleButtons()) {
          canvas.drawRect(
              button.left.toFloat(),
              button.top.toFloat(),
              button.right.toFloat(),
              button.bottom.toFloat(),
              debugPaint!!
          )
        }
        // Button outlines
        debugPaint!!.color = debugColorPink
        debugPaint!!.style = Style.STROKE
        for (button in getVisibleButtons()) {
          canvas.drawRect(
              button.left.toFloat(),
              button.top.toFloat(),
              button.right.toFloat(),
              button.bottom.toFloat(),
              debugPaint!!
          )
        }
        debugPaint!!.style = Style.FILL_AND_STROKE
        canvas.drawRect(
            0f,
            measuredHeight - buttonFramePadding.toFloat(),
            measuredWidth.toFloat(),
            measuredHeight.toFloat()
            , debugPaint!!
        )
      } else {
        // Hollow button frame fill
        debugPaint!!.color = Color.WHITE
        val topY = (measuredHeight - (buttonFrameHeight - buttonFramePadding)).toFloat()
        val bottomY = (measuredHeight - buttonFramePadding).toFloat()
        canvas.drawRect(
            mainFrameTop.toFloat(),
            topY,
            measuredWidth - buttonFramePadding.toFloat(),
            bottomY,
            debugPaint!!
        )
        // Button highlight and spacing fill
        debugPaint!!.color = debugColorTeal
        for (button in getVisibleButtons()) {
          canvas.drawRect(
              button.left.toFloat(),
              button.top.toFloat(),
              button.right.toFloat(),
              button.bottom.toFloat(), debugPaint!!
          )
        }
      }
    }
  }
}
