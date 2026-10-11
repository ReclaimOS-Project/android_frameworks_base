/*
 * SPDX-FileCopyrightText: 2026 The ReclaimOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.collection.coordinator

import android.annotation.StringRes
import android.content.res.Resources
import android.view.LayoutInflater
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.notification.collection.ListEntry
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifComparator
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifPromoter
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.statusbar.notification.reclaimos.ReclaimosNotificationClassifier
import com.android.systemui.statusbar.notification.reclaimos.ReclaimosNotificationClassifier.Category
import com.android.systemui.statusbar.notification.reclaimos.ReclaimosSectionHeaderController
import com.android.systemui.statusbar.notification.stack.BUCKET_RECLAIMOS_CALLS
import com.android.systemui.statusbar.notification.stack.BUCKET_RECLAIMOS_MESSAGES
import com.android.systemui.statusbar.notification.stack.BUCKET_RECLAIMOS_NOTIFICATIONS
import javax.inject.Inject

/**
 * ReclaimOS notifications-v1: arranges the notifications on the lock screen.
 *
 * While the lock screen is showing (StatusBarState.KEYGUARD), and only then:
 * - every notification goes into one of three sections, MESSAGES, PHONE CALLS and NOTIFICATIONS,
 *   in that order, each with a heading and sorted newest first;
 * - groups are split into separate rows (their summary is dropped, as for any group that loses
 *   all its children).
 *
 * It decides only the order and the sections. Which notifications are shown, and whether their
 * contents are hidden, stays with the stock coordinators. The pulled-down shade, locked or not,
 * uses the stock sections and groups.
 */
@CoordinatorScope
class ReclaimosLockscreenCoordinator
@Inject
constructor(
    @Main resources: Resources,
    @ShadeDisplayAware layoutInflater: LayoutInflater,
    private val statusBarStateController: StatusBarStateController,
    private val classifier: ReclaimosNotificationClassifier,
    private val visualStabilityCoordinator: VisualStabilityCoordinator,
) : Coordinator {

    private val enabled = resources.getBoolean(R.bool.config_reclaimosLockscreen)
    private var onKeyguard = false
    private var lastTopLevelEntry: ListEntry? = null

    val messagesSectioner: NotifSectioner =
        LockscreenSectioner(
            "ReclaimosMessages",
            BUCKET_RECLAIMOS_MESSAGES,
            Category.MESSAGES,
            R.string.reclaimos_lockscreen_heading_messages,
            layoutInflater,
        )

    val callsSectioner: NotifSectioner =
        LockscreenSectioner(
            "ReclaimosCalls",
            BUCKET_RECLAIMOS_CALLS,
            Category.CALLS,
            R.string.reclaimos_lockscreen_heading_calls,
            layoutInflater,
        )

    // Takes every notification on the lock screen that is not a message or a call.
    val notificationsSectioner: NotifSectioner =
        LockscreenSectioner(
            "ReclaimosNotifications",
            BUCKET_RECLAIMOS_NOTIFICATIONS,
            null,
            R.string.reclaimos_lockscreen_heading_notifications,
            layoutInflater,
        )

    private val groupSplitter =
        object : NotifPromoter("ReclaimosLockscreenGroups") {
            override fun shouldPromoteToTopLevel(child: NotificationEntry): Boolean = isActive()
        }

    private val stateListener =
        object : StatusBarStateController.StateListener {
            override fun onStateChanged(newState: Int) {
                val nowOnKeyguard = newState == StatusBarState.KEYGUARD
                if (nowOnKeyguard == onKeyguard) return
                onKeyguard = nowOnKeyguard
                // Switch between the lock screen layout and the stock one right away. Without
                // this, visual stability would keep the old sections and groups while the screen
                // is on: the ReclaimOS ones in the pulled-down shade, or the stock ones on the
                // lock screen after the shade is closed.
                val reason =
                    if (onKeyguard) "ReclaimOS lock screen shown" else "ReclaimOS lock screen hidden"
                visualStabilityCoordinator.allowEveryChangeForNextRun(reason)
                // Invalidating one sectioner re-sections every notification.
                messagesSectioner.invalidateList(reason)
            }
        }

    override fun attach(pipeline: NotifPipeline) {
        if (!enabled) return
        onKeyguard = statusBarStateController.state == StatusBarState.KEYGUARD
        statusBarStateController.addCallback(stateListener)
        pipeline.addPromoter(groupSplitter)
        // A new default Phone or SMS app can move notifications between sections. Invalidating
        // one sectioner re-sections every notification.
        classifier.addOnRolesChangedListener {
            messagesSectioner.invalidateList("ReclaimOS default Phone or SMS app changed")
        }
        pipeline.addOnBeforeRenderListListener { list -> lastTopLevelEntry = list.lastOrNull() }
        pipeline.addOnAfterRenderEntryListener { entry, _ ->
            // A divider below every lock screen row except the last one.
            entry.row?.setReclaimosLockscreenDivider(
                lastTopLevelEntry?.representativeEntry != entry
            )
        }
    }

    private fun isActive(): Boolean =
        enabled && statusBarStateController.state == StatusBarState.KEYGUARD

    private inner class LockscreenSectioner(
        name: String,
        bucket: Int,
        private val category: Category?,
        @StringRes headerTextResId: Int,
        layoutInflater: LayoutInflater,
    ) : NotifSectioner(name, bucket) {

        private val headerController =
            ReclaimosSectionHeaderController(name, layoutInflater, headerTextResId)

        private val newestFirst =
            object : NotifComparator("${name}NewestFirst") {
                override fun compare(o1: ListEntry, o2: ListEntry): Int =
                    postedTime(o2).compareTo(postedTime(o1))
            }

        override fun isInSection(entry: ListEntry): Boolean {
            if (!isActive()) return false
            if (category == null) return true
            val notification = entry.representativeEntry ?: return false
            return classifier.classify(notification) == category
        }

        override fun getComparator(): NotifComparator = newestFirst

        override fun getHeaderNodeController(): NodeController = headerController
    }

    private companion object {
        /** The time the row shows: the notification's time, or when it was posted. */
        fun postedTime(entry: ListEntry): Long {
            val sbn = entry.representativeEntry?.sbn ?: return 0
            return sbn.notification.getWhen().takeIf { it != 0L } ?: sbn.postTime
        }
    }
}
