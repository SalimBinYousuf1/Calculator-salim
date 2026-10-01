package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppMode
import com.example.viewmodel.CalculatorUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisplaySection(
    uiState: CalculatorUiState,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val exprScrollState = rememberScrollState()
    val resultScrollState = rememberScrollState()
    val stepsListState = rememberLazyListState()
    var showContextMenu by remember { mutableStateOf(false) }

    // Auto scroll active expression to end
    LaunchedEffect(uiState.expression, exprScrollState.maxValue) {
        if (uiState.expression.isNotEmpty()) {
            exprScrollState.scrollTo(exprScrollState.maxValue)
        }
    }

    // Auto scroll result to end when long digits appear
    val heroText = uiState.finalResult ?: uiState.livePreview
    LaunchedEffect(heroText, resultScrollState.maxValue) {
        if (heroText != null) {
            resultScrollState.scrollTo(resultScrollState.maxValue)
        }
    }

    // Auto scroll paper-tape steps list to bottom as calculations move up
    LaunchedEffect(uiState.steps.size) {
        if (uiState.steps.isNotEmpty()) {
            stepsListState.animateScrollToItem(uiState.steps.size - 1)
        }
    }

    val copyToClipboard: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Salim Result", text)
        clipboard.setPrimaryClip(clip)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onShowSnackbar("Copied $text")
    }

    val shareCalculation: () -> Unit = {
        val shareText = if (uiState.finalResult != null) {
            "${uiState.expression} = ${uiState.finalResult}"
        } else {
            uiState.expression
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Calculation")
        context.startActivity(shareIntent)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (uiState.expression.isNotEmpty() || uiState.finalResult != null || uiState.steps.isNotEmpty()) {
                        showContextMenu = true
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("display_section")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Mode Badges Row (DEG / RAD)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (uiState.appMode == AppMode.SCIENTIFIC) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = if (uiState.isRadMode) "RAD" else "DEG",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (uiState.isMemorySet) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = "M",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Scrollable continuous calculation paper tape
            if (uiState.steps.isNotEmpty()) {
                LazyColumn(
                    state = stepsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .padding(bottom = 6.dp)
                        .testTag("continuous_steps_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = uiState.steps, key = { it.id }) { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { copyToClipboard(step.result) }
                                ),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = step.expression,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.End,
                                softWrap = false,
                                maxLines = 1
                            )
                            Text(
                                text = step.result, // No '=' prefix!
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                textAlign = TextAlign.End,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Active Expression Row (horizontally scrollable, prevents digit wrapping/jumbling)
            val expressionText = if (uiState.expression.isEmpty() && uiState.finalResult == null) "0" else uiState.expression
            val isFinalState = uiState.isCalculated && uiState.finalResult != null

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(exprScrollState),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = expressionText,
                        color = if (isFinalState) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = if (isFinalState) 22.sp else getDynamicExpressionSize(expressionText.length),
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        style = TextStyle(
                            fontFeatureSettings = "tnum",
                            letterSpacing = 0.sp
                        ),
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.testTag("text_expression")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Result Row: BOLD, BIGGER than expression, NO '=' symbol, with direct COPY icon
            val isError = uiState.errorMessage != null

            AnimatedVisibility(
                visible = heroText != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (heroText != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direct copy icon as soon as result is computed (with or without pressing =)
                        if (!isError) {
                            FilledIconButton(
                                onClick = { copyToClipboard(heroText) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_copy_result"),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy result",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Big, Bold result without '=' prefix, in horizontally scrollable container with tabular numbers
                        Box(
                            modifier = Modifier.weight(1f, fill = false),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(resultScrollState),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = heroText, // No '=' prefix!
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error
                                        isFinalState -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    fontSize = getDynamicResultSize(heroText.length),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        letterSpacing = 0.sp
                                    ),
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("text_result")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Long-Press Context Menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            val textToCopy = uiState.finalResult ?: uiState.livePreview ?: uiState.expression
            DropdownMenuItem(
                text = { Text("Copy result") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    if (textToCopy.isNotEmpty()) {
                        copyToClipboard(textToCopy)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Copy expression") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    if (uiState.expression.isNotEmpty()) {
                        copyToClipboard(uiState.expression)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = {
                    showContextMenu = false
                    shareCalculation()
                }
            )
        }
    }
}

private fun getDynamicExpressionSize(length: Int): androidx.compose.ui.unit.TextUnit {
    return when {
        length > 40 -> 16.sp
        length > 28 -> 19.sp
        length > 20 -> 23.sp
        length > 14 -> 28.sp
        length > 8 -> 34.sp
        else -> 40.sp
    }
}

private fun getDynamicResultSize(length: Int): androidx.compose.ui.unit.TextUnit {
    return when {
        length > 30 -> 18.sp
        length > 22 -> 22.sp
        length > 16 -> 28.sp
        length > 12 -> 34.sp
        length > 8 -> 40.sp
        length > 5 -> 46.sp
        else -> 52.sp
    }
}
