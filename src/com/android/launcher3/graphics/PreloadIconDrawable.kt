/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.graphics

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Paint.Cap.ROUND
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.util.FloatProperty
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.ColorUtils
import com.android.app.animation.Interpolators
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.util.Themes
import kotlin.math.max
import kotlin.math.min

/** Extension of [FastBitmapDrawable] which shows a progress bar around the icon. */
class PreloadIconDrawable(
    private val item: ItemInfoWithIcon,
    private val indicatorColor: Int,
    preloadColors: IntArray,
    private val isDarkMode: Boolean,
    // Path in [0, 100] bounds.
    private val shapePath: Path,
) : FastBitmapDrawable(item.bitmap) {

    constructor(
        info: ItemInfoWithIcon,
        context: Context,
    ) : this(
        info,
        IconPalette.getPreloadProgressColor(context, info.bitmap.color),
        getPreloadColors(context),
        Utilities.isDarkTheme(context),
        ThemeManager.INSTANCE[context].iconShape.getPath(DEFAULT_PATH_SIZE.toFloat()),
    )

    private val tmpMatrix = Matrix()
    private val pathMeasure = PathMeasure()

    private val scaledPlatePath = Path()
    private val scaledTrackPath = Path()
    private val scaledProgressPath = Path()
    private val progressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            strokeCap = ROUND
            alpha = MAX_PAINT_ALPHA
        }

    private val systemAccentColor: Int = preloadColors[PRELOAD_ACCENT_COLOR_INDEX]
    private val systemBackgroundColor: Int = preloadColors[PRELOAD_BACKGROUND_COLOR_INDEX]

    private var progressColor: Int
    private var trackColor: Int
    private var plateColor: Int

    private var trackLength = 0f

    private var ranFinishAnimation = false

    // Progress of the internal state. [0, 1] indicates the fraction of completed progress,
    // [1, (1 + COMPLETE_ANIM_FRACTION)] indicates the progress of zoom animation.
    private var internalStateProgress = 0f

    // This multiplier is used to animate scale when going from 0 to non-zero and expanding
    private val invalidateRunnable = Runnable { this.invalidateSelf() }
    private val iconScaleMultiplier = AnimatedFloat(invalidateRunnable)

    @get:VisibleForTesting
    var activeAnimation: ObjectAnimator? = null
        private set

    init {
        // Progress color
        val m3HCT = FloatArray(3)
        ColorUtils.colorToM3HCT(item.bitmap.color, m3HCT)
        progressColor =
            ColorUtils.M3HCTToColor(
                m3HCT[0],
                m3HCT[1],
                if (isDarkMode) max(m3HCT[2].toDouble(), 55.0).toFloat()
                else min(m3HCT[2].toDouble(), 40.0).toFloat(),
            )

        // Track color
        trackColor = ColorUtils.M3HCTToColor(m3HCT[0], 16f, (if (isDarkMode) 30 else 90).toFloat())
        // Plate color
        plateColor =
            ColorUtils.M3HCTToColor(
                m3HCT[0],
                (if (isDarkMode) 36 else 24).toFloat(),
                (if (isDarkMode) (if (isThemed()) 10 else 20) else 80).toFloat(),
            )

        // If it's a pending app we will animate scale and alpha when it's no longer pending.
        iconScaleMultiplier.updateValue((if (item.progressLevel == 0) 0 else 1).toFloat())

        level = item.progressLevel
        // Set a disabled icon color if the app is suspended or if the app is pending download
        isDisabled = item.isDisabled || item.isPendingDownload
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)

        val progressWidth = bounds.width() * PROGRESS_BOUNDS_SCALE
        val plateGapWidth = bounds.width() * PROGRESS_BOUNDS_SCALE / 2f

        tmpMatrix.setScale(
            (bounds.width() - 2 * progressWidth) / DEFAULT_PATH_SIZE,
            (bounds.height() - 2 * progressWidth) / DEFAULT_PATH_SIZE,
        )
        tmpMatrix.postTranslate(bounds.left + progressWidth, bounds.top + progressWidth)
        shapePath.transform(tmpMatrix, scaledTrackPath)
        progressPaint.strokeWidth = PROGRESS_STROKE_SCALE * bounds.width()

        tmpMatrix.setScale(
            (bounds.width() - 2 * plateGapWidth) / DEFAULT_PATH_SIZE,
            (bounds.height() - 2 * plateGapWidth) / DEFAULT_PATH_SIZE,
        )
        tmpMatrix.postTranslate(bounds.left + plateGapWidth, bounds.top + plateGapWidth)
        shapePath.transform(tmpMatrix, scaledPlatePath)

        pathMeasure.setPath(scaledTrackPath, true)
        trackLength = pathMeasure.length

        setInternalProgress(internalStateProgress)
    }

    public override fun drawInternal(canvas: Canvas, bounds: Rect) {
        if (ranFinishAnimation) {
            super.drawInternal(canvas, bounds)
            return
        }
        if (Flags.enableLauncherIconShapes()) {
            drawShapedProgressIcon(canvas, bounds)
        } else {
            drawDefaultProgressIcon(canvas, bounds)
        }
    }

    private fun drawShapedProgressIcon(canvas: Canvas, bounds: Rect) {
        if (internalStateProgress > 0f) {
            if (internalStateProgress < 1f) {
                // Draw icon at scale UNDER the progress and background paths.
                drawIconAtScale(canvas, bounds)
            }
            drawBackgroundPlate(canvas, bounds)
            drawTrackAndProgress(canvas)
            if (internalStateProgress >= 1f) {
                // Draw icon at scale animating OVER the progress and background path.
                drawIconAtScale(canvas, bounds)
            }
        } else {
            // Just draw Icon when no progress
            drawIconAtScale(canvas, bounds)
        }
    }

    /**
     * Draw background plate as a stroke around icon. Uses total stroke width for gap + progress, so
     * that progress can be overlaid to leave gap.
     */
    private fun drawBackgroundPlate(canvas: Canvas, bounds: Rect) {
        val width = canvas.width.toFloat()
        canvas.save()
        canvas.scale(PLATE_SCALE, PLATE_SCALE, bounds.exactCenterX(), bounds.exactCenterY())
        progressPaint.style = STROKE
        progressPaint.strokeWidth = width * TOTAL_STROKE_SCALE
        progressPaint.color = plateColor
        canvas.drawPath(scaledPlatePath, progressPaint)
        canvas.restore()
    }

    /** Draws track around icon with gap, and draws progress bar according to current progress. */
    private fun drawTrackAndProgress(canvas: Canvas) {
        canvas.save()
        progressPaint.style = STROKE
        progressPaint.strokeWidth = canvas.width * PROGRESS_STROKE_SCALE
        progressPaint.color = trackColor
        canvas.drawPath(scaledTrackPath, progressPaint)
        progressPaint.alpha = MAX_PAINT_ALPHA
        progressPaint.color = progressColor
        canvas.drawPath(scaledProgressPath, progressPaint)
        canvas.restore()
    }

    private fun drawDefaultProgressIcon(canvas: Canvas, bounds: Rect) {
        if (internalStateProgress > 0) {
            // Draw background.
            progressPaint.style = FILL
            progressPaint.color = plateColor
            canvas.drawPath(scaledTrackPath, progressPaint)
        }

        if (internalStateProgress > 0) {
            // Draw track and progress.
            progressPaint.style = STROKE
            progressPaint.color = trackColor
            canvas.drawPath(scaledTrackPath, progressPaint)
            progressPaint.alpha = MAX_PAINT_ALPHA
            progressPaint.color = progressColor
            canvas.drawPath(scaledProgressPath, progressPaint)
        }

        drawIconAtScale(canvas, bounds)
    }

    /** Draws just the icon to scale */
    private fun drawIconAtScale(canvas: Canvas, bounds: Rect) {
        canvas.save()
        val scale = 1 - iconScaleMultiplier.value * (1 - SMALL_ICON_SCALE)
        canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())
        super.drawInternal(canvas, bounds)
        canvas.restore()
    }

    /** Updates the install progress based on the level */
    override fun onLevelChange(level: Int): Boolean {
        // Run the animation if we have already been bound.
        updateInternalState(level * 0.01f, false, null)
        return true
    }

    /** Runs the finish animation if it is has not been run after last call to [.onLevelChange] */
    fun maybePerformFinishedAnimation(oldIcon: PreloadIconDrawable, onFinishCallback: Runnable?) {
        progressColor = oldIcon.progressColor
        trackColor = oldIcon.trackColor
        plateColor = oldIcon.plateColor

        if (oldIcon.internalStateProgress >= 1) {
            internalStateProgress = oldIcon.internalStateProgress
        }

        // If the drawable was recently initialized, skip the progress animation.
        if (internalStateProgress == 0f) {
            internalStateProgress = 1f
        }
        updateInternalState(1 + COMPLETE_ANIM_FRACTION, true, onFinishCallback)
    }

    fun hasNotCompleted(): Boolean = !ranFinishAnimation

    private fun updateInternalState(
        finalProgress: Float,
        isFinish: Boolean,
        onFinishCallback: Runnable?,
    ) {
        activeAnimation?.cancel()
        activeAnimation = null

        val animateProgress = finalProgress >= internalStateProgress && bounds.width() > 0
        if (!animateProgress || ranFinishAnimation) {
            setInternalProgress(finalProgress)
            if (isFinish) onFinishCallback?.run()
        } else {
            activeAnimation =
                ObjectAnimator.ofFloat(this, INTERNAL_STATE, finalProgress).also {
                    it.duration =
                        ((finalProgress - internalStateProgress) * DURATION_SCALE).toLong()
                    it.interpolator = Interpolators.LINEAR

                    if (isFinish) {
                        it.addListener(
                            AnimatorListeners.forEndCallback(Runnable { ranFinishAnimation = true })
                        )
                        if (onFinishCallback != null)
                            it.addListener(AnimatorListeners.forEndCallback(onFinishCallback))
                    }
                    it.start()
                }
        }
    }

    /**
     * Sets the internal progress and updates the UI accordingly
     *
     * for progress <= 0:
     * - icon is pending
     * - progress track is not visible
     * - progress bar is not visible
     *
     * for progress < 1:
     * - icon without pending motion
     * - progress track is visible
     * - progress bar is visible. Progress bar is drawn as a fraction of [scaledTrackPath].
     *
     * @see PathMeasure.getSegment
     */
    private fun setInternalProgress(progress: Float) {
        // Animate scale and alpha from pending to downloading state.
        if (progress > 0 && internalStateProgress == 0f) {
            // Progress is changing for the first time, animate the icon scale
            iconScaleMultiplier.animateToValue(1f).apply {
                duration = SCALE_AND_ALPHA_ANIM_DURATION
                interpolator = Interpolators.EMPHASIZED
                start()
            }
        }

        internalStateProgress = progress
        if (progress <= 0) {
            iconScaleMultiplier.updateValue(0f)
        } else {
            pathMeasure.getSegment(
                0f,
                (min(progress.toDouble(), 1.0) * trackLength).toFloat(),
                scaledProgressPath,
                true,
            )
            if (progress > 1) {
                // map the scale back to original value
                iconScaleMultiplier.updateValue(
                    Utilities.mapBoundToRange(
                        progress - 1,
                        0f,
                        COMPLETE_ANIM_FRACTION,
                        1f,
                        0f,
                        Interpolators.EMPHASIZED,
                    )
                )
            }
        }
        invalidateSelf()
    }

    public override fun newConstantState(): FastBitmapConstantState {
        return PreloadIconConstantState(
            bitmapInfo,
            item,
            indicatorColor,
            intArrayOf(systemAccentColor, systemBackgroundColor),
            isDarkMode,
            shapePath,
        )
    }

    protected class PreloadIconConstantState(
        bitmapInfo: BitmapInfo,
        protected val mInfo: ItemInfoWithIcon,
        protected val mIndicatorColor: Int,
        protected val mPreloadColors: IntArray,
        protected val mIsDarkMode: Boolean,
        private val mShapePath: Path,
    ) : FastBitmapConstantState(bitmapInfo) {

        public override fun createDrawable(): PreloadIconDrawable {
            return PreloadIconDrawable(
                mInfo,
                mIndicatorColor,
                mPreloadColors,
                mIsDarkMode,
                mShapePath,
            )
        }
    }

    companion object {
        private val INTERNAL_STATE: FloatProperty<PreloadIconDrawable> =
            object : FloatProperty<PreloadIconDrawable>("internalStateProgress") {
                override fun get(obj: PreloadIconDrawable): Float = obj.internalStateProgress

                override fun setValue(obj: PreloadIconDrawable, value: Float) =
                    obj.setInternalProgress(value)
            }

        private const val DEFAULT_PATH_SIZE = 100
        private const val MAX_PAINT_ALPHA = 255

        private const val DURATION_SCALE: Long = 500
        private const val SCALE_AND_ALPHA_ANIM_DURATION: Long = 500

        // The smaller the number, the faster the animation would be.
        // Duration = COMPLETE_ANIM_FRACTION * DURATION_SCALE
        private const val COMPLETE_ANIM_FRACTION = 1f

        private const val SMALL_ICON_SCALE = 0.8f
        private const val PROGRESS_STROKE_SCALE = 0.055f
        private const val PROGRESS_BOUNDS_SCALE = 0.075f
        private const val TOTAL_STROKE_SCALE = 3 * PROGRESS_STROKE_SCALE / 2

        // Scale for canvas when drawing plate stroke. This is to avoid gaps between icon and plate.
        // We use icon scale + 2 * plate gap width. This is the same as icon scale + progress scale.
        private const val PLATE_SCALE = SMALL_ICON_SCALE + PROGRESS_STROKE_SCALE

        private const val PRELOAD_ACCENT_COLOR_INDEX = 0
        private const val PRELOAD_BACKGROUND_COLOR_INDEX = 1

        private fun getPreloadColors(context: Context): IntArray {
            val preloadColors = IntArray(2)
            preloadColors[PRELOAD_ACCENT_COLOR_INDEX] =
                Themes.getAttrColor(context, R.attr.preloadIconAccentColor)
            preloadColors[PRELOAD_BACKGROUND_COLOR_INDEX] =
                Themes.getAttrColor(context, R.attr.preloadIconBackgroundColor)
            return preloadColors
        }

        /** Returns a FastBitmapDrawable with the icon. */
        @JvmStatic
        fun newPendingIcon(context: Context, info: ItemInfoWithIcon): PreloadIconDrawable {
            return PreloadIconDrawable(info, context)
        }
    }
}
