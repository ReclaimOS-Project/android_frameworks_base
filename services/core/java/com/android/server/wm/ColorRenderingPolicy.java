/*
 * SPDX-FileCopyrightText: 2026 The ReclaimOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.FIRST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
import static android.view.WindowManager.LayoutParams.LAST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_STARTING;

import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Process;
import android.os.UserHandle;

import com.android.server.pm.pkg.PackageStateInternal;

/**
 * ReclaimOS renders everything in grayscale except app windows of a small, OS-defined set of
 * packages. This class decides which window surfaces get the color permission; SurfaceFlinger
 * enforces grayscale on every layer that does not carry it.
 *
 * The decision is made per window, not per activity or task, so that surfaces WindowManager
 * places in an allowed app's hierarchy on behalf of others (IME, letterbox, starting windows,
 * dims, thumbnails) stay gray.
 */
class ColorRenderingPolicy {

    private final PackageManagerInternal mPmInternal;
    private final String[] mAllowedPackages;

    ColorRenderingPolicy(Context context, PackageManagerInternal pmInternal) {
        mPmInternal = pmInternal;
        mAllowedPackages = context.getResources().getStringArray(
                com.android.internal.R.array.config_reclaimOsColorAllowedPackages);
    }

    /** Whether the surface of {@code win} may be composited in color. */
    boolean isColorAllowed(WindowState win) {
        if (win.isChildWindow()) {
            final int type = win.mAttrs.type;
            if (type < FIRST_SUB_WINDOW || type > LAST_SUB_WINDOW) {
                return false;
            }
            final WindowState parent = win.getTopParentWindow();
            if (parent.mSession.mUid != win.mSession.mUid) {
                return false;
            }
            return isAllowedAppWindow(parent);
        }
        return isAllowedAppWindow(win);
    }

    /** An app window added by the activity's own process, other than its starting window. */
    private boolean isAllowedAppWindow(WindowState win) {
        final int type = win.mAttrs.type;
        if (type < FIRST_APPLICATION_WINDOW || type > LAST_APPLICATION_WINDOW
                || type == TYPE_APPLICATION_STARTING) {
            return false;
        }
        final ActivityRecord activity = win.mActivityRecord;
        if (activity == null || activity.getUid() != win.mSession.mUid) {
            return false;
        }
        return isAllowedUid(win.mSession.mUid);
    }

    /**
     * Resolves the allowlist through PackageManager rather than trusting anything the app
     * provides. Only preinstalled packages with their own uid qualify, so neither a sideloaded
     * package with the same name nor a shared system uid can pick up the permission.
     */
    private boolean isAllowedUid(int uid) {
        if (UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID) {
            return false;
        }
        final int userId = UserHandle.getUserId(uid);
        for (String packageName : mAllowedPackages) {
            final PackageStateInternal ps = mPmInternal.getPackageStateInternal(packageName);
            if (ps == null || !ps.isSystem() || ps.hasSharedUser()) {
                continue;
            }
            if (UserHandle.getUid(userId, ps.getAppId()) == uid
                    && ps.getUserStateOrDefault(userId).isInstalled()) {
                return true;
            }
        }
        return false;
    }
}
