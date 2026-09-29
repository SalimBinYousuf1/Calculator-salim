package com.example.ui.components

import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClearRed
import com.example.ui.theme.ClearRedDarkBg
import com.example.ui.theme.ClearRedLightBg

enum class ButtonType {
    NUMBER,
    FUNCTION,
    OPERATOR,
    ACCENT,
    SCIENTIFIC,
    CLEAR_RED
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.NUMBER,
    fontSize: TextUnit = 24.sp,
    testTag: String = "btn_$text",
    isActive: Boolean = false,
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = false,
    content: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth subtle press scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "btn_scale"
    )

    val colorScheme = MaterialTheme.colorScheme
    val isDarkBackground = colorScheme.background.red < 0.2f

    val (bgColor, contentColor) = when (type) {
        ButtonType.CLEAR_RED -> {
            if (isDarkBackground) {
                ClearRedDarkBg to ClearRed
            } else {
                ClearRedLightBg to ClearRed
            }
        }
        ButtonType.ACCENT -> {
            colorScheme.primary to colorScheme.onPrimary
        }
        ButtonType.OPERATOR -> {
            if (isActive) {
                colorScheme.primary to colorScheme.onPrimary
            } else {
                colorScheme.surfaceVariant to colorScheme.primary
            }
        }
        ButtonType.FUNCTION -> {
            colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        }
        ButtonType.SCIENTIFIC -> {
            if (isActive) {
                colorScheme.primary.copy(alpha = 0.2f) to colorScheme.primary
            } else {
                colorScheme.surfaceVariant.copy(alpha = 0.65f) to colorScheme.onSurfaceVariant
            }
        }
        ButtonType.NUMBER -> {
            colorScheme.surface to colorScheme.onSurface
        }
    }

    Box(
        modifier = modifier
            .padding(4.dp)
            .scale(scale)
            .defaultMinSize(minWidth = 52.dp, minHeight = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(bgColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (soundEnabled) {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                    }
                    if (hapticEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        if (soundEnabled) {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        it()
                    }
                }
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else {
            Text(
                text = text,
                color = contentColor,
                fontSize = fontSize,
                fontWeight = if (type == ButtonType.NUMBER) FontWeight.Normal else FontWeight.Medium
            )
        }
    }
}
