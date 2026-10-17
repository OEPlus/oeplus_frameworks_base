/*
 * SPDX-FileCopyrightText: The OEPlus Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.common.ui.compose

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import com.android.systemui.res.R
import com.android.systemui.shared.system.BlurUtils

/**
 * Puts the OEPlus glass material behind this element: a blur region in the element's shape, which
 * SurfaceFlinger draws as glass. Returns the modifier unchanged unless the glass theme is on, so
 * the stock UI creates nothing extra.
 *
 * @param alpha opacity of the glass, e.g. to follow a fade the element's parent applies
 */
@Composable
fun Modifier.oeplusGlass(shape: CornerBasedShape, alpha: () -> Float = { 1f }): Modifier {
    if (!BlurUtils.isOEPlusGlassEnabled()) {
        return this
    }
    val viewRootImpl = LocalView.current.viewRootImpl ?: return this
    val drawable =
        remember(viewRootImpl) {
            viewRootImpl.createBackgroundBlurDrawable().apply {
                // Blend over the content below instead of clearing it, like notification cards.
                setXfermode(null)
            }
        }
    val blurRadius = dimensionResource(R.dimen.notification_background_blur_radius)
    return drawBehind {
        drawable.setBlurRadius(blurRadius.roundToPx())
        drawable.setCornerRadius(
            shape.topStart.toPx(size, this),
            shape.topEnd.toPx(size, this),
            shape.bottomStart.toPx(size, this),
            shape.bottomEnd.toPx(size, this),
        )
        drawable.setAlpha((alpha() * 255f).toInt().coerceIn(0, 255))
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
