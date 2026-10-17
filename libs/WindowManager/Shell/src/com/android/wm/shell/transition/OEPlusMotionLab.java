/*
 * SPDX-FileCopyrightText: The OEPlus Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.wm.shell.transition;

import android.os.SystemProperties;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * OEPlus lab switches for the default window and task transitions, read for every
 * transition so a change applies to the next animation:
 *
 * <ul>
 *   <li>{@code debug.oeplus.anim.curve}: {@code x1,y1,x2,y2} of a cubic Bezier that
 *       replaces the curve of the movement parts (translate, scale, clip). Fades keep
 *       their own curve. Empty means stock.</li>
 *   <li>{@code debug.oeplus.anim.duration_pct}: duration and start offsets in percent of
 *       stock (100 = stock).</li>
 * </ul>
 */
final class OEPlusMotionLab {
    private static final String CURVE = "debug.oeplus.anim.curve";
    private static final String DURATION_PCT = "debug.oeplus.anim.duration_pct";

    private OEPlusMotionLab() {}

    static Animation apply(Animation a) {
        if (a == null) return null;
        final Interpolator curve = parseCurve(SystemProperties.get(CURVE, ""));
        final int pct = Math.max(10, Math.min(300, SystemProperties.getInt(DURATION_PCT, 100)));
        if (curve == null && pct == 100) return a;
        adjust(a, curve, pct);
        return a;
    }

    private static void adjust(Animation a, Interpolator curve, int pct) {
        if (a instanceof AnimationSet set) {
            // A set that shares its interpolator applies its own to every child
            if (curve != null) set.setInterpolator(curve);
            for (Animation child : set.getAnimations()) {
                adjust(child, curve, pct);
            }
        } else if (curve != null && !(a instanceof AlphaAnimation)) {
            a.setInterpolator(curve);
        }
        if (pct != 100) {
            if (a.getDuration() > 0) a.setDuration(a.getDuration() * pct / 100);
            a.setStartOffset(a.getStartOffset() * pct / 100);
        }
    }

    /** Returns the curve for "x1,y1,x2,y2", or null when unset or malformed. */
    private static Interpolator parseCurve(String value) {
        final String[] parts = value.split(",");
        if (parts.length != 4) return null;
        try {
            final float x1 = Float.parseFloat(parts[0].trim());
            final float y1 = Float.parseFloat(parts[1].trim());
            final float x2 = Float.parseFloat(parts[2].trim());
            final float y2 = Float.parseFloat(parts[3].trim());
            if (x1 < 0 || x1 > 1 || x2 < 0 || x2 > 1) return null;
            return new PathInterpolator(x1, y1, x2, y2);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
