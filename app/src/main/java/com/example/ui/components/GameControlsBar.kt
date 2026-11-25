package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameControlsBar(
    hapticsEnabled: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRefactor: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RepeatableControlButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_move_left),
            hapticsEnabled = hapticsEnabled,
            onAction = onMoveLeft,
            modifier = Modifier
                .weight(1f)
                .testTag("control_left")
        )
        RepeatableControlButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_soft_drop),
            hapticsEnabled = hapticsEnabled,
            onAction = onSoftDrop,
            modifier = Modifier
                .weight(1f)
                .testTag("control_down")
        )
        TapControlButton(
            icon = Icons.Rounded.VerticalAlignBottom,
            contentDescription = stringResource(R.string.cd_hard_drop),
            containerColor = AmberWarm.copy(alpha = 0.18f),
            borderColor = AmberWarm.copy(alpha = 0.65f),
            iconTint = AmberWarm,
            hapticsEnabled = hapticsEnabled,
            onAction = onHardDrop,
            modifier = Modifier
                .weight(1f)
                .testTag("control_hard_drop")
        )
        TapControlButton(
            icon = Icons.Rounded.AspectRatio,
            contentDescription = stringResource(R.string.cd_refactor),
            containerColor = CyanElectric,
            borderColor = CyanElectric,
            iconTint = MidnightBg,
            hapticsEnabled = hapticsEnabled,
            onAction = onRefactor,
            modifier = Modifier
                .weight(1.2f)
                .testTag("control_refactor")
        )
        RepeatableControlButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_move_right),
            hapticsEnabled = hapticsEnabled,
            onAction = onMoveRight,
            modifier = Modifier
                .weight(1f)
                .testTag("control_right")
        )
    }
}

@Composable
private fun TapControlButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    borderColor: Color,
    iconTint: Color,
    hapticsEnabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onAction()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun RepeatableControlButton(
    icon: ImageVector,
    contentDescription: String,
    hapticsEnabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnAction by rememberUpdatedState(onAction)
    val currentHaptics by rememberUpdatedState(hapticsEnabled)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SlateSurfaceVariant)
            .border(1.2.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .semantics {
                this.contentDescription = contentDescription
                onClick(label = contentDescription) {
                    if (currentHaptics) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    currentOnAction()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (currentHaptics) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    currentOnAction()
                    val repeatJob = scope.launch {
                        delay(220L)
                        while (true) {
                            currentOnAction()
                            delay(95L)
                        }
                    }
                    waitForUpOrCancellation()
                    repeatJob.cancel()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}
