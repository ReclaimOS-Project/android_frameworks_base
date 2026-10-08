/*
 * Copyright (C) 2024 The Android Open Source Project
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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.customization.R as customR
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject

class KeyguardSliceViewSection
@Inject
constructor(
    private val context: Context,
    val smartspaceController: LockscreenSmartspaceController,
    private val keyguardClockViewModel: KeyguardClockViewModel,
) : KeyguardSection() {
    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        if (smartspaceController.isEnabled) return

        constraintLayout.findViewById<View?>(R.id.keyguard_slice_view)?.let {
            (it.parent as ViewGroup).removeView(it)
            constraintLayout.addView(it)
            // ReclaimOS lockscreen-v1: center the date under the centered clock.
            if (context.resources.getBoolean(R.bool.config_reclaimosLockscreen)) {
                it.findViewById<TextView?>(R.id.title)?.gravity = Gravity.CENTER_HORIZONTAL
                it.findViewById<LinearLayout?>(R.id.row)?.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    override fun bindData(constraintLayout: ConstraintLayout) {}

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        if (smartspaceController.isEnabled) return

        constraintSet.apply {
            connect(
                R.id.keyguard_slice_view,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                context.resources.getDimensionPixelSize(customR.dimen.clock_padding_start) +
                    context.resources.getDimensionPixelSize(customR.dimen.status_view_margin_horizontal),
            )
            connect(
                R.id.keyguard_slice_view,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            // ReclaimOS lockscreen-v1: same horizontal span as the centered small clock
            // (see ClockSection), so the date stays centered beneath it.
            if (context.resources.getBoolean(R.bool.config_reclaimosLockscreen)) {
                constrainWidth(R.id.keyguard_slice_view, ConstraintSet.MATCH_CONSTRAINT)
                connect(
                    R.id.keyguard_slice_view,
                    ConstraintSet.END,
                    if (keyguardClockViewModel.clockShouldBeCentered.value) ConstraintSet.PARENT_ID
                    else R.id.split_shade_guideline,
                    ConstraintSet.END,
                    context.resources.getDimensionPixelSize(customR.dimen.clock_padding_start) +
                        context.resources.getDimensionPixelSize(
                            customR.dimen.status_view_margin_horizontal
                        ),
                )
            }
            constrainHeight(R.id.keyguard_slice_view, ConstraintSet.WRAP_CONTENT)

            connect(
                R.id.keyguard_slice_view,
                ConstraintSet.TOP,
                customR.id.lockscreen_clock_view,
                ConstraintSet.BOTTOM
            )

            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(R.id.keyguard_slice_view)
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {}
}
