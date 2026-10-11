/*
 * SPDX-FileCopyrightText: 2026 The ReclaimOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.reclaimos

import android.app.Notification
import android.app.Notification.MessagingStyle
import android.app.Person
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.android.internal.widget.MessagingMessage
import com.android.systemui.statusbar.notification.reclaimos.ReclaimosNotificationClassifier.Category
import com.android.systemui.statusbar.notification.row.HybridGroupManager
import java.util.Date

/** ReclaimOS notifications-v1: what one text row on the lock screen shows. */
data class ReclaimosLockscreenContent(
    val title: CharSequence?,
    /** The time the notification was posted, or null when the app hides it. */
    val time: CharSequence?,
    val preview: CharSequence?,
    /** For notifications that show a running timer: its base, in elapsedRealtime. */
    val chronometerBase: Long? = null,
    val chronometerCountDown: Boolean = false,
)

/**
 * ReclaimOS notifications-v1: reads a lock screen text row's title, time and preview from the
 * notification's own fields. It never changes them and never adds content of its own.
 *
 * When the lock screen hides the notification's contents (`redacted`), only the app name, the
 * app's public version of the notification and the posting time are read, as on the stock
 * public card.
 */
object ReclaimosLockscreenContentExtractor {

    // Previews show at most two lines; there is no point in copying more text than this.
    private const val MAX_TEXT_LENGTH = 500

    @JvmStatic
    fun extract(
        context: Context,
        sbn: StatusBarNotification,
        appName: CharSequence?,
        category: Category,
        redacted: Boolean,
    ): ReclaimosLockscreenContent {
        val notification = sbn.notification
        val app = appName?.takeIf { it.isNotEmpty() } ?: sbn.packageName
        val chronometer = notification.showsChronometer()
        val time = if (chronometer) null else formatTime(context, notification, sbn.postTime)
        val chronometerBase =
            if (chronometer) {
                notification.getWhen() + (SystemClock.elapsedRealtime() - System.currentTimeMillis())
            } else {
                null
            }
        val countDown =
            chronometer && notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)

        val titleAndPreview: Pair<CharSequence?, CharSequence?> =
            when {
                redacted -> app to redactedPreview(context, notification.publicVersion, app)
                category == Category.MESSAGES ->
                    messageTitleAndPreview(context, notification)
                        ?: ((resolveTitle(notification) ?: app) to resolveText(notification))
                category == Category.CALLS -> {
                    val caller =
                        notification.extras
                            .getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java)
                            ?.name
                    (clean(caller) ?: resolveTitle(notification) ?: app) to
                        resolveText(notification)
                }
                else -> app to joinTitleAndText(notification, app)
            }

        return ReclaimosLockscreenContent(
            title = titleAndPreview.first,
            time = time,
            preview = titleAndPreview.second,
            chronometerBase = chronometerBase,
            chronometerCountDown = countDown,
        )
    }

    /** The public version's text if the app supplied one, otherwise the stock "Unlock to view". */
    private fun redactedPreview(
        context: Context,
        publicVersion: Notification?,
        app: CharSequence,
    ): CharSequence =
        publicVersion?.let { joinTitleAndText(it, app) }
            ?: context.getString(com.android.systemui.res.R.string.redacted_notification_single_line_text)

    /**
     * For MessagingStyle notifications: the conversation name (or the sender, for one-to-one
     * chats) and the latest message, prefixed with its sender in group chats. Null when the
     * notification carries no messages.
     */
    private fun messageTitleAndPreview(
        context: Context,
        notification: Notification,
    ): Pair<CharSequence?, CharSequence?>? {
        val extras = notification.extras
        @Suppress("DEPRECATION")
        val messages =
            MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
        val last = messages.lastOrNull() ?: return null
        val user = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        val userName = user?.name ?: extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
        val conversationTitle = clean(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))

        val title =
            if (isGroup) {
                conversationTitle
                    ?: resolveTitle(notification)
                    ?: context.getString(
                        com.android.internal.R.string.conversation_title_fallback_group_chat
                    )
            } else {
                // The latest message may be the user's own reply; name the other person.
                val otherSender =
                    messages.lastOrNull { it.senderPerson != null && it.senderPerson != user }
                conversationTitle
                    ?: clean(otherSender?.senderPerson?.name)
                    ?: resolveTitle(notification)
                    ?: context.getString(
                        com.android.internal.R.string.conversation_title_fallback_one_to_one
                    )
            }

        var text = clean(last.text)
        if (text == null && MessagingMessage.hasImage(last)) {
            text =
                context.getString(
                    com.android.internal.R.string.conversation_single_line_image_placeholder
                )
        }
        val sender = clean(last.senderPerson?.name ?: userName)
        val preview =
            if (isGroup && text != null && sender != null) {
                context.getString(
                    com.android.internal.R.string.conversation_single_line_name_display,
                    sender,
                ) + " " + text
            } else {
                text
            }
        return title to preview
    }

    /** "Title: text", or whichever of the two the notification has. A title equal to the app name is left out. */
    private fun joinTitleAndText(notification: Notification, app: CharSequence): CharSequence? {
        val title = resolveTitle(notification)?.takeIf { it.toString() != app.toString() }
        val text = resolveText(notification)
        return when {
            title != null && text != null -> "$title: $text"
            else -> title ?: text
        }
    }

    private fun resolveTitle(notification: Notification): CharSequence? =
        clean(HybridGroupManager.resolveTitle(notification))

    private fun resolveText(notification: Notification): CharSequence? =
        clean(HybridGroupManager.resolveText(notification))

    /** Plain text on one line (spans and line breaks dropped), or null when empty. */
    private fun clean(text: CharSequence?): String? {
        if (text.isNullOrEmpty()) return null
        val plain =
            text.subSequence(0, minOf(text.length, MAX_TEXT_LENGTH))
                .toString()
                .replace(Regex("\\s+"), " ")
                .trim()
        return plain.ifEmpty { null }
    }

    /** The posting time today, or the date for older notifications. Null when the app hides it. */
    private fun formatTime(context: Context, notification: Notification, postTime: Long): String? {
        if (!notification.showsTime()) return null
        val time = notification.getWhen().takeIf { it != 0L } ?: postTime
        return if (DateUtils.isToday(time)) {
            DateFormat.getTimeFormat(context).format(Date(time))
        } else {
            DateUtils.formatDateTime(
                context,
                time,
                DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_ABBREV_MONTH or
                    DateUtils.FORMAT_NO_YEAR,
            )
        }
    }
}
