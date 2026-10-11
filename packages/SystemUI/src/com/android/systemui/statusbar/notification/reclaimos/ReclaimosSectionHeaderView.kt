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
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.stack.SectionHeaderView

/**
 * ReclaimOS notifications-v1: a lock screen notification heading (MESSAGES, PHONE CALLS,
 * NOTIFICATIONS). Unlike the stock section headers it is plain text: it has no click action and
 * no clear button. It stays an accessibility heading.
 */
class ReclaimosSectionHeaderView(context: Context, attrs: AttributeSet?) :
    SectionHeaderView(context, attrs) {

    private lateinit var contentView: View
    private lateinit var labelView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        contentView = requireViewById(R.id.content)
        labelView = requireViewById(R.id.header_label)
        // SectionHeaderView announces an "open settings" click action; this heading has none.
        ViewCompat.removeAccessibilityAction(labelView, AccessibilityActionCompat.ACTION_CLICK.id)
        labelView.isClickable = false
        applyStyle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Follow the light/dark theme, font scale and the short/tall screen spacing.
        applyStyle()
    }

    private fun applyStyle() {
        val res = context.resources
        val sidePadding = res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_row_side_padding)
        contentView.setPaddingRelative(
            sidePadding,
            res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_heading_padding_top),
            sidePadding,
            res.getDimensionPixelSize(R.dimen.reclaimos_lockscreen_heading_padding_bottom),
        )
        labelView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            res.getDimension(R.dimen.reclaimos_lockscreen_heading_text_size),
        )
        labelView.letterSpacing = res.getFloat(R.dimen.reclaimos_lockscreen_heading_letter_spacing)
        labelView.setTextColor(context.getColor(R.color.reclaimos_lockscreen_text_secondary))
        requestLayout()
    }
}
