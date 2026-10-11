/*
 * SPDX-FileCopyrightText: 2026 The ReclaimOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.reclaimos

import android.annotation.StringRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.render.NodeController

/**
 * ReclaimOS notifications-v1: the node controller for one lock screen heading. The notification
 * pipeline only adds it to the stack when its section has notifications and section headers are
 * visible (on the lock screen), so empty headings never show.
 */
class ReclaimosSectionHeaderController(
    override val nodeLabel: String,
    private val layoutInflater: LayoutInflater,
    @StringRes private val headerTextResId: Int,
) : NodeController {

    private var headerView: ReclaimosSectionHeaderView? = null

    override val view: View
        get() =
            headerView
                ?: (layoutInflater.inflate(
                        R.layout.reclaimos_lockscreen_section_header,
                        null,
                        false, /* attachToRoot */
                    ) as ReclaimosSectionHeaderView)
                    .also {
                        it.layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                        it.setHeaderText(headerTextResId)
                        headerView = it
                    }

    override fun onViewAdded() {
        headerView?.setContentVisibleAnimated(true)
    }

    override fun offerToKeepInParentForAnimation(): Boolean = false

    override fun removeFromParentIfKeptForAnimation(): Boolean = false

    override fun resetKeepInParentForAnimation() {}
}
