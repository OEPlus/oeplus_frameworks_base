/*
 * SPDX-FileCopyrightText: The OEPlus Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.common.ui.view

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.android.systemui.res.R
import com.android.systemui.shared.system.BlurUtils

/**
 * View counterpart of `Modifier.oeplusGlass()`: puts the OEPlus glass material (a blur region
 * SurfaceFlinger draws as glass) under a view's background while the view is attached. Does
 * nothing unless the glass theme is on.
 *
 * The view must not be drawn into an offscreen layer (e.g. LAYER_TYPE_HARDWARE): blur regions
 * are positioned relative to the window.
 */
object OEPlusGlassViews {

    @JvmStatic
    fun attach(view: View, cornerRadius: Float) {
        if (!BlurUtils.isOEPlusGlassEnabled()) {
            return
        }
        val listener =
            object : View.OnAttachStateChangeListener {
                private var original: Drawable? = null
                private var applied: Drawable? = null

                override fun onViewAttachedToWindow(v: View) {
                    val viewRootImpl = v.viewRootImpl ?: return
                    val glass =
                        viewRootImpl.createBackgroundBlurDrawable().apply {
                            // Blend over the content below instead of clearing it.
                            setXfermode(null)
                            setBlurRadius(
                                v.resources.getDimensionPixelSize(
                                    R.dimen.notification_background_blur_radius
                                )
                            )
                            setCornerRadius(cornerRadius)
                        }
                    original = v.background
                    applied = original?.let { LayerDrawable(arrayOf(glass, it)) } ?: glass
                    v.background = applied
                }

                override fun onViewDetachedFromWindow(v: View) {
                    // Leave the background alone if someone replaced it meanwhile.
                    if (applied != null && v.background === applied) {
                        v.background = original
                    }
                    original = null
                    applied = null
                }
            }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) {
            listener.onViewAttachedToWindow(view)
        }
    }
}
