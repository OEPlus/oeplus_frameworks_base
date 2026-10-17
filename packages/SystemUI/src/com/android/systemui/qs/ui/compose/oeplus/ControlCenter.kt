/*
 * SPDX-FileCopyrightText: The OEPlus Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.ui.compose.oeplus

import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.volume.data.repository.AudioRepository
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.common.ui.compose.oeplusGlass
import com.android.systemui.res.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Spacing = 12.dp
private val SliderWidth = 72.dp
private val SliderShape = RoundedCornerShape(28.dp)
private val CardShape = RoundedCornerShape(28.dp)
private val MusicStream = AudioStream(AudioManager.STREAM_MUSIC)

/**
 * OEPlus glass theme: Quick Settings arranged like iOS Control Center. The media module and tall
 * vertical brightness and media volume sliders sit in the top row, on glass; the tiles (round
 * glass buttons in this theme) follow below.
 */
@Composable
fun ControlCenterLayout(
    brightnessViewModel: BrightnessSliderViewModel?,
    audioRepository: AudioRepository,
    mediaVisible: Boolean,
    media: @Composable () -> Unit,
    tiles: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.qs_media_session_height_expanded)),
            horizontalArrangement = Arrangement.spacedBy(Spacing),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (mediaVisible) media() else NotPlayingCard(Modifier.fillMaxSize())
            }
            if (brightnessViewModel != null) {
                VerticalBrightnessSlider(
                    viewModel = brightnessViewModel,
                    modifier = Modifier.width(SliderWidth).fillMaxHeight(),
                )
            }
            VerticalVolumeSlider(
                audioRepository = audioRepository,
                modifier = Modifier.width(SliderWidth).fillMaxHeight(),
            )
        }
        tiles()
    }
}

@Composable
private fun NotPlayingCard(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .oeplusGlass(CardShape)
                .clip(CardShape)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(20.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_volume_media),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopStart).size(28.dp),
        )
        Text(
            text = stringResource(R.string.oeplus_cc_not_playing),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun VerticalBrightnessSlider(viewModel: BrightnessSliderViewModel, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val min = viewModel.minBrightness.value
    val max = viewModel.maxBrightness.value
    val gamma = viewModel.currentBrightness.value
    val fraction =
        if (max <= min || gamma < min) 0f else ((gamma - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    val toGamma = { f: Float -> GammaBrightness((min + f * (max - min)).roundToInt()) }
    // No setIsDragging(): that starts the brightness mirror, which only draws the stock slider.
    VerticalGlassSlider(
        fraction = fraction,
        icon = BrightnessSliderViewModel.getIconForPercentage(fraction * 100f),
        contentDescription = stringResource(R.string.oeplus_cc_brightness),
        onChange = { f -> scope.launch { viewModel.onDrag(Drag.Dragging(toGamma(f))) } },
        onStop = { f -> scope.launch { viewModel.onDrag(Drag.Stopped(toGamma(f))) } },
        modifier = modifier,
    )
}

@Composable
private fun VerticalVolumeSlider(audioRepository: AudioRepository, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val stream by
        remember(audioRepository) { audioRepository.getAudioStream(MusicStream) }
            .collectAsStateWithLifecycle(initialValue = null)
    val model = stream
    val min = model?.minVolume ?: 0
    val max = model?.maxVolume ?: 1
    val fraction =
        if (model == null || max <= min) 0f
        else ((model.volume - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    val setFraction = { f: Float ->
        scope.launch {
            audioRepository.setVolume(MusicStream, (min + f * (max - min)).roundToInt())
        }
        Unit
    }
    VerticalGlassSlider(
        fraction = fraction,
        icon = if (fraction == 0f) R.drawable.ic_volume_media_mute else R.drawable.ic_volume_media,
        contentDescription = stringResource(R.string.oeplus_cc_volume),
        onChange = setFraction,
        onStop = setFraction,
        modifier = modifier,
    )
}

/**
 * A tall glass pill filled from the bottom, like iOS Control Center's sliders. Dragging moves the
 * value relative to where it was, instead of jumping to the finger.
 */
@Composable
private fun VerticalGlassSlider(
    fraction: Float,
    @DrawableRes icon: Int,
    contentDescription: String,
    onChange: (Float) -> Unit,
    onStop: (Float) -> Unit,
    modifier: Modifier,
) {
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnStop by rememberUpdatedState(onStop)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged ?: fraction

    Box(
        modifier =
            modifier
                .oeplusGlass(SliderShape)
                .clip(SliderShape)
                .background(Color.White.copy(alpha = 0.08f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragged = currentFraction },
                        onDragEnd = {
                            dragged?.let { currentOnStop(it) }
                            dragged = null
                        },
                        onDragCancel = {
                            dragged?.let { currentOnStop(it) }
                            dragged = null
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val next =
                            ((dragged ?: currentFraction) - dragAmount / size.height).coerceIn(
                                0f,
                                1f,
                            )
                        dragged = next
                        currentOnChange(next)
                    }
                }
                .semantics {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                    setProgress { value ->
                        currentOnStop(value.coerceIn(0f, 1f))
                        true
                    }
                }
    ) {
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(shown)
                    .background(Color.White.copy(alpha = 0.92f))
        )
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (shown > 0.2f) Color.Black.copy(alpha = 0.65f) else Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(26.dp),
        )
    }
}
