/*
 * SPDX-FileCopyrightText: 2026 The ReclaimOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.reclaimos

import android.app.Notification
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.os.UserHandle
import android.util.Log
import android.util.SparseArray
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * ReclaimOS notifications-v1: sorts lock screen notifications into the MESSAGES, PHONE CALLS and
 * NOTIFICATIONS headings.
 *
 * It uses the notification's own metadata first (category, style, conversation ranking). Apps that
 * post messages or calls without that metadata are recognized by being the user's default SMS or
 * Phone app (the SMS and Dialer roles). It never looks at notification text.
 */
@SysUISingleton
class ReclaimosNotificationClassifier
@Inject
constructor(private val roleManager: RoleManager, @Main private val mainExecutor: Executor) {

    enum class Category {
        MESSAGES,
        CALLS,
        NOTIFICATIONS,
    }

    private class RoleHolders(val dialer: Set<String>, val sms: Set<String>)

    // Role holders per user id. Only used on the main thread.
    private val roleHolders = SparseArray<RoleHolders>()
    private val rolesChangedListeners = mutableListOf<Runnable>()
    private var listeningToRoles = false

    private val onRoleHoldersChangedListener = OnRoleHoldersChangedListener { roleName, user ->
        if (roleName == RoleManager.ROLE_DIALER || roleName == RoleManager.ROLE_SMS) {
            roleHolders.remove(user.identifier)
            rolesChangedListeners.forEach { it.run() }
        }
    }

    /** Runs [listener] on the main thread when the default Phone or SMS app changes. */
    fun addOnRolesChangedListener(listener: Runnable) {
        rolesChangedListeners.add(listener)
        if (!listeningToRoles) {
            listeningToRoles = true
            try {
                roleManager.addOnRoleHoldersChangedListenerAsUser(
                    mainExecutor,
                    onRoleHoldersChangedListener,
                    UserHandle.ALL,
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "Cannot observe role holders", e)
            }
        }
    }

    fun classify(entry: NotificationEntry): Category {
        val sbn = entry.sbn
        val notification = sbn.notification
        return when {
            isCall(notification) -> Category.CALLS
            isMessage(notification) || entry.ranking.isConversation -> Category.MESSAGES
            else -> {
                val holders = getRoleHolders(sbn.normalizedUserId)
                when (sbn.packageName) {
                    in holders.dialer -> Category.CALLS
                    in holders.sms -> Category.MESSAGES
                    else -> Category.NOTIFICATIONS
                }
            }
        }
    }

    private fun getRoleHolders(userId: Int): RoleHolders {
        roleHolders.get(userId)?.let {
            return it
        }
        return try {
            val user = UserHandle.of(userId)
            RoleHolders(
                    dialer = roleManager.getRoleHoldersAsUser(RoleManager.ROLE_DIALER, user).toSet(),
                    sms = roleManager.getRoleHoldersAsUser(RoleManager.ROLE_SMS, user).toSet(),
                )
                .also { roleHolders.put(userId, it) }
        } catch (e: RuntimeException) {
            // Not cached, so the next pipeline run asks again.
            Log.w(TAG, "Cannot read role holders for user $userId", e)
            RoleHolders(emptySet(), emptySet())
        }
    }

    companion object {
        private const val TAG = "ReclaimosNotifClassifier"

        private fun isCall(notification: Notification): Boolean =
            notification.category == Notification.CATEGORY_CALL ||
                notification.category == Notification.CATEGORY_MISSED_CALL ||
                notification.isStyle(Notification.CallStyle::class.java)

        private fun isMessage(notification: Notification): Boolean =
            notification.category == Notification.CATEGORY_MESSAGE ||
                notification.isStyle(Notification.MessagingStyle::class.java)

        /**
         * Whether [notification] is an incoming, ongoing or screening call. These keep their stock
         * card on the lock screen so the answer and hang-up buttons stay available.
         */
        @JvmStatic
        fun isInteractiveCall(notification: Notification): Boolean {
            if (notification.isStyle(Notification.CallStyle::class.java)) {
                val callType = notification.extras.getInt(Notification.EXTRA_CALL_TYPE)
                return callType == Notification.CallStyle.CALL_TYPE_INCOMING ||
                    callType == Notification.CallStyle.CALL_TYPE_ONGOING ||
                    callType == Notification.CallStyle.CALL_TYPE_SCREENING
            }
            return notification.category == Notification.CATEGORY_CALL &&
                (notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                    notification.fullScreenIntent != null)
        }
    }
}
