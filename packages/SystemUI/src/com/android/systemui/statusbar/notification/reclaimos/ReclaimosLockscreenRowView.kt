/*
 * SPDX-FileCopyrightText: 2026 The ReclaimOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.reclaimos

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import com.android.systemui.res.R

/**
 * ReclaimOS notifications-v1: the text-only lock screen presentation of a notification. It lives
 * inside ExpandableNotificationRow, which shows it instead of the notification card while the
 * lock screen is showing; the row keeps handling taps, swipes and accessibility actions.
 */
class ReclaimosLockscreenRowView(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {

    private lateinit var titleRow: View
    private lateinit var titleView: TextView
    private lateinit var timeView: TextView
    private lateinit var chronometerView: Chronometer
    private lateinit var previewView: TextView
    private lateinit var bottomSpace: View
    private lateinit var dividerView: View

    override fun onFinishInflate() {
        super.onFinishInflate()
        titleView = requireViewById(R.id.reclaimos_row_title)
        titleRow = titleView.parent as View
        timeView = requireViewById(R.id.reclaimos_row_time)
        chronometerView = requireViewById(R.id.reclaimos_row_chronometer)
        previewView = requireViewById(R.id.reclaimos_row_preview)
        bottomSpace = requireViewById(R.id.reclaimos_row_bottom_space)
        dividerView = requireViewById(R.id.reclaimos_row_divider)
        applyStyle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Follow the light/dark theme, font scale and the short/tall screen spacing.
        applyStyle()
    }

    fun bind(content: ReclaimosLockscreenContent) {
        titleView.text = content.title
        timeView.text = content.time
        timeView.visibility = if (content.time.isNullOrEmpty()) GONE else VISIBLE
        val chronometerBase = content.chronometerBase
        if (chronometerBase != null) {
            chronometerView.base = chronometerBase
            chronometerView.isCountDown = content.chronometerCountDown
            chronometerView.visibility = VISIBLE
            // Chronometer only ticks while it is shown.
            chronometerView.start()
        } else {
            chronometerView.stop()
            chronometerView.visibility = GONE
        }
        previewView.text = content.preview
        previewView.visibility = if (content.preview.isNullOrEmpty()) GONE else VISIBLE
    }

    /** The divider is drawn below every row except the last one on the lock screen. */
    fun setDividerVisible(visible: Boolean) {
        // INVISIBLE keeps the row height the same either way.
        dividerView.visibility = if (visible) VISIBLE else INVISIBLE
    }

    private fun applyStyle() {
        val res = context.resources
        val sidePadding = res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_row_side_padding)
        setPaddingRelative(sidePadding, 0, sidePadding, 0)
        titleRow.setPaddingRelative(
            0,
            res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_row_padding_top),
            0,
            0,
        )
        (previewView.layoutParams as MarginLayoutParams).topMargin =
            res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_row_preview_margin_top)
        bottomSpace.layoutParams.height =
            res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_row_padding_bottom)
        dividerView.layoutParams.height =
            res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_divider_height)
        setTextSize(titleView, R.dimen.reclaimos_lockscreen_row_title_text_size)
        setTextSize(timeView, R.dimen.reclaimos_lockscreen_row_time_text_size)
        setTextSize(chronometerView, R.dimen.reclaimos_lockscreen_row_time_text_size)
        setTextSize(previewView, R.dimen.reclaimos_lockscreen_row_preview_text_size)
        titleView.setTextColor(context.getColor(R.color.reclaimos_lockscreen_text_primary))
        timeView.setTextColor(context.getColor(R.color.reclaimos_lockscreen_text_secondary))
        chronometerView.setTextColor(context.getColor(R.color.reclaimos_lockscreen_text_secondary))
        previewView.setTextColor(context.getColor(R.color.reclaimos_lockscreen_text_secondary))
        dividerView.setBackgroundColor(context.getColor(R.color.reclaimos_lockscreen_divider))
        requestLayout()
    }

    private fun setTextSize(view: TextView, dimenRes: Int) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(dimenRes))
    }
}
