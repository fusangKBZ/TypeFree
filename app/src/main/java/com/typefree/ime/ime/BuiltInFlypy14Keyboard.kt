package com.typefree.ime.ime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.typefree.ime.i18n.tr
import com.typefree.ime.ime.engine.Flypy14KeyHelper
import com.typefree.ime.ime.engine.Flypy14KeySpec
import com.typefree.ime.ui.components.TypeFreeIcons
import com.typefree.ime.ui.theme.LocalTypeFreeKeyboardPalette
import com.typefree.ime.ui.theme.TypeFreeEmerald
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Flypy14Layer {
    MAIN,
    NUMBER,
    SYMBOL
}

private enum class Flypy14Gesture {
    NONE,
    TAP,
    LEFT,
    RIGHT,
    UP
}

private data class Flypy14PopupPreview(
    val token: Long,
    val primaryLabel: String,
    val hintLabel: String?,
    val isHighlighted: Boolean,
    val centerXInRootPx: Float,
    val keyTopInRootPx: Float
)

private val FLYPY14_NUM_ROW_1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val FLYPY14_NUM_ROW_2 = listOf("@", "#", "$", "%", "*", "-", "+", "(", ")", "/")
private val FLYPY14_NUM_ROW_3 = listOf(";", "=", "？", "！", ":", "\"", "'")

private val FLYPY14_CN_SYM_ROW_1 = listOf("，", "。", "？", "！", "、", "：", "；", "“", "”", "…")
private val FLYPY14_CN_SYM_ROW_2 = listOf("（", "）", "【", "】", "《", "》", "「", "」", "—", "～")
private val FLYPY14_CN_SYM_ROW_3 = listOf("¥", "·", "‘", "’", "•", "°", "`")

private val FLYPY14_EN_SYM_ROW_1 = listOf("[", "]", "{", "}", "<", ">", "^", "&", "=", "~")
private val FLYPY14_EN_SYM_ROW_2 = listOf("_", "\\", "|", "~", "!", "@", "#", "$", "%", "*")
private val FLYPY14_EN_SYM_ROW_3 = listOf("€", "£", "+", "-", "/", ":", "?")

private val FLYPY14_CELL_HPAD = 2.1.dp
private val FLYPY14_CELL_VPAD = 2.4.dp
private val FLYPY14_KEY_CORNER = 6.5.dp
private val FLYPY14_BOTTOM_RIM = 1.2.dp

/**
 * Pixel-proportioned iFlytek-style 14-key Xiaohe Shuangpin keyboard (`double_pinyin_flypy_14`):
 * - Row 1 (`1.00f`): `QW(1)` `ER(2)` `TY(3)` `UI(4)` `OP(5)` (5 × 2.00f white)
 * - Row 2 (`1.00f`): `AS(6)` `DF(7)` `GH(8)` `JK(9)` `L(0)`  (5 × 2.00f white)
 * - Row 3 (`1.03f`): `'` (0.99f grey) · `ZX(~)` (2.03f) · `CV(!)` (2.03f) · `BN(@)` (2.03f) · `M(?)` (1.66f) · `⌫` (1.26f grey)
 * - Row 4 (`1.04f`): `123` (1.37f) · `符` (1.38f) · `，` (1.09f) · Space+Mic (2.31f) · `。` (1.09f) · `中/英` (1.39f) · `清除/回车` (1.37f)
 * - Displays compact Xiaohe final/initial hints underneath each letter; supports tap (ambiguous),
 *   swipe-left/right (exact single letter), and swipe-up (top symbol `1-0`, `~`, `!`, `@`, `?`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuiltInFlypy14Keyboard(
    enterActionInfo: EnterActionInfo,
    isFlypyChineseMode: Boolean,
    flypyComposingCode: String,
    flypyCandidateCount: Int,
    commaPeriodQuickSelect: Boolean,
    onCommitChar: (String) -> Unit,
    onCommitSecondarySymbol: (String) -> Unit = onCommitChar,
    onMoveCursorByStep: (Int) -> Unit = {},
    onCommaOrPeriodKey: (Boolean) -> Unit,
    onInsertSpace: () -> Unit,
    onToggleFlypyLanguageMode: () -> Unit,
    onClearComposing: () -> Unit = {},
    onFlushComposingBeforeLayerSwitch: () -> Unit,
    onBackspace: () -> Unit,
    onSwipeUpBackspaceUndo: () -> Unit,
    backspaceSwipeUpClearEnabled: Boolean = false,
    onSwipeUpBackspaceClear: () -> Unit = {},
    onEnterOrNewline: () -> Unit,
    onSwitchToVoice: () -> Unit = {},
    onSwitchSystemIme: () -> Unit,
    inputSessionToken: Int = 0,
    showKeyPopupPreview: Boolean = true,
    onSwipeUpPeriodSlash: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    var currentLayer by remember { mutableStateOf(Flypy14Layer.MAIN) }
    var isMoreSymbols by remember { mutableStateOf(false) }

    var rootPosInWindow by remember { mutableStateOf(Offset.Zero) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var previewSeq by remember { mutableLongStateOf(0L) }
    var activeKeyPreview by remember { mutableStateOf<Flypy14PopupPreview?>(null) }

    val hasActiveComposing = flypyComposingCode.isNotEmpty()
    val isInSubLayer = currentLayer != Flypy14Layer.MAIN

    LaunchedEffect(inputSessionToken) {
        currentLayer = Flypy14Layer.MAIN
        isMoreSymbols = false
        activeKeyPreview = null
    }

    fun showPreviewForKey(
        primaryLabel: String,
        hintLabel: String?,
        isHighlighted: Boolean,
        keyPosInWindow: Offset,
        keySize: IntSize
    ): Long {
        val token = ++previewSeq
        val centerXInRoot = (keyPosInWindow.x - rootPosInWindow.x) + keySize.width / 2f
        val keyTopInRoot = keyPosInWindow.y - rootPosInWindow.y
        activeKeyPreview = Flypy14PopupPreview(
            token = token,
            primaryLabel = primaryLabel,
            hintLabel = hintLabel,
            isHighlighted = isHighlighted,
            centerXInRootPx = centerXInRoot,
            keyTopInRootPx = keyTopInRoot
        )
        return token
    }

    fun clearPreviewToken(token: Long) {
        if (activeKeyPreview?.token == token) {
            activeKeyPreview = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootPosInWindow = coords.positionInWindow()
                rootSizePx = coords.size
            }
            .padding(horizontal = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (currentLayer == Flypy14Layer.MAIN) {
                    val rows = Flypy14KeyHelper.KEY_ROWS
                    // Row 1 (weight 1.00f): 5 × 2.00f = 10.00f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.00f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        rows[0].forEach { spec ->
                            Flypy14LetterKey(
                                spec = spec,
                                onCommitToken = onCommitChar,
                                onCommitTopSymbol = onCommitSecondarySymbol,
                                onPreviewUpdate = ::showPreviewForKey,
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(2.00f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    // Row 2 (weight 1.00f): 5 × 2.00f = 10.00f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.00f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        rows[1].forEach { spec ->
                            Flypy14LetterKey(
                                spec = spec,
                                onCommitToken = onCommitChar,
                                onCommitTopSymbol = onCommitSecondarySymbol,
                                onPreviewUpdate = ::showPreviewForKey,
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(2.00f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    // Row 3 (weight 1.03f): 0.99 + 2.03 + 2.03 + 2.03 + 1.66 + 1.26 = 10.00f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.03f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Left delimiter key `'` (grey, 0.99f)
                        Flypy14FunctionKey(
                            label = "’",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(0.99f)
                                .fillMaxHeight(),
                            onClick = {
                                if (hasActiveComposing) {
                                    onCommitChar("'")
                                } else {
                                    onCommitSecondarySymbol("'")
                                }
                            }
                        )

                        // ZX, CV, BN (2.03f each) and M (1.66f)
                        val row3Weights = floatArrayOf(2.03f, 2.03f, 2.03f, 1.66f)
                        rows[2].forEachIndexed { idx, spec ->
                            Flypy14LetterKey(
                                spec = spec,
                                onCommitToken = onCommitChar,
                                onCommitTopSymbol = onCommitSecondarySymbol,
                                onPreviewUpdate = ::showPreviewForKey,
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(row3Weights[idx])
                                    .fillMaxHeight()
                            )
                        }

                        // Backspace (grey, 1.26f)
                        Flypy14RepeatingBackspaceKey(
                            onBackspace = onBackspace,
                            onSwipeUpAction = if (backspaceSwipeUpClearEnabled) onSwipeUpBackspaceClear else onSwipeUpBackspaceUndo,
                            modifier = Modifier
                                .weight(1.26f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    val (row1, row2, row3) = when (currentLayer) {
                        Flypy14Layer.NUMBER -> Triple(FLYPY14_NUM_ROW_1, FLYPY14_NUM_ROW_2, FLYPY14_NUM_ROW_3)
                        Flypy14Layer.SYMBOL -> if (isMoreSymbols) {
                            Triple(FLYPY14_EN_SYM_ROW_1, FLYPY14_EN_SYM_ROW_2, FLYPY14_EN_SYM_ROW_3)
                        } else {
                            Triple(FLYPY14_CN_SYM_ROW_1, FLYPY14_CN_SYM_ROW_2, FLYPY14_CN_SYM_ROW_3)
                        }
                        Flypy14Layer.MAIN -> Triple(FLYPY14_NUM_ROW_1, FLYPY14_NUM_ROW_2, FLYPY14_NUM_ROW_3)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.00f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        row1.forEach { sym ->
                            Flypy14SimpleKey(
                                label = sym,
                                fontSize = 17.sp,
                                onPreviewStart = { label, pos, size -> showPreviewForKey(label, null, false, pos, size) },
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = { onCommitSecondarySymbol(sym) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.00f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        row2.forEach { sym ->
                            Flypy14SimpleKey(
                                label = sym,
                                fontSize = 16.sp,
                                onPreviewStart = { label, pos, size -> showPreviewForKey(label, null, false, pos, size) },
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = { onCommitSecondarySymbol(sym) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.03f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Flypy14FunctionKey(
                            label = when {
                                currentLayer == Flypy14Layer.NUMBER -> "符"
                                isMoreSymbols -> "中文"
                                else -> "英文"
                            },
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight(),
                            onClick = {
                                if (currentLayer == Flypy14Layer.NUMBER) {
                                    currentLayer = Flypy14Layer.SYMBOL
                                    isMoreSymbols = false
                                } else {
                                    isMoreSymbols = !isMoreSymbols
                                }
                            }
                        )

                        row3.forEach { sym ->
                            Flypy14SimpleKey(
                                label = sym,
                                fontSize = 16.5.sp,
                                onPreviewStart = { label, pos, size -> showPreviewForKey(label, null, false, pos, size) },
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = { onCommitSecondarySymbol(sym) }
                            )
                        }

                        Flypy14RepeatingBackspaceKey(
                            onBackspace = onBackspace,
                            onSwipeUpAction = if (backspaceSwipeUpClearEnabled) onSwipeUpBackspaceClear else onSwipeUpBackspaceUndo,
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight()
                        )
                    }
                }

                // Row 4 (weight 1.04f): 1.37 + 1.38 + 1.09 + 2.31 + 1.09 + 1.39 + 1.37 = 10.00f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.04f),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // 1. [123] (1.37f, grey)
                    Flypy14FunctionKey(
                        label = if (currentLayer == Flypy14Layer.NUMBER) tr("返回", "Back", "返回") else "123",
                        fontSize = 13.5.sp,
                        isActiveHighlight = currentLayer == Flypy14Layer.NUMBER,
                        modifier = Modifier
                            .weight(1.37f)
                            .fillMaxHeight(),
                        onClick = {
                            if (currentLayer == Flypy14Layer.NUMBER) {
                                currentLayer = Flypy14Layer.MAIN
                            } else {
                                if (hasActiveComposing) onFlushComposingBeforeLayerSwitch()
                                currentLayer = Flypy14Layer.NUMBER
                            }
                            isMoreSymbols = false
                        }
                    )

                    // 2. [符] (1.38f, grey)
                    Flypy14FunctionKey(
                        label = if (currentLayer == Flypy14Layer.SYMBOL) tr("返回", "Back", "返回") else "符",
                        fontSize = 14.5.sp,
                        isActiveHighlight = currentLayer == Flypy14Layer.SYMBOL,
                        modifier = Modifier
                            .weight(1.38f)
                            .fillMaxHeight(),
                        onClick = {
                            if (currentLayer == Flypy14Layer.SYMBOL) {
                                currentLayer = Flypy14Layer.MAIN
                            } else {
                                if (hasActiveComposing) onFlushComposingBeforeLayerSwitch()
                                currentLayer = Flypy14Layer.SYMBOL
                            }
                            isMoreSymbols = false
                        }
                    )

                    val showSecondBadge = hasActiveComposing && commaPeriodQuickSelect && flypyCandidateCount >= 2
                    val showThirdBadge = hasActiveComposing && commaPeriodQuickSelect && flypyCandidateCount >= 3
                    val isIdleChineseComma = isFlypyChineseMode && !isInSubLayer && !hasActiveComposing
                    val isIdlePeriod = !isInSubLayer && !hasActiveComposing

                    // 3. [，] (1.09f, white)
                    Flypy14PunctKey(
                        label = if (isFlypyChineseMode && !isInSubLayer) "，" else ",",
                        swipeUpBadge = if (showSecondBadge) "2" else null,
                        highlightSwipeUpBadge = showSecondBadge,
                        modifier = Modifier
                            .weight(1.09f)
                            .fillMaxHeight(),
                        onClick = {
                            if (isInSubLayer) {
                                onCommitSecondarySymbol(",")
                            } else {
                                onCommaOrPeriodKey(true)
                            }
                        },
                        onSwipeUp = if (isIdleChineseComma) ({ onCommitChar(";") }) else null
                    )

                    // 4. Spacebar with Mic-on-Bracket icon (2.31f, white)
                    Flypy14SpaceBarKey(
                        onTap = onInsertSpace,
                        onMoveCursorByStep = onMoveCursorByStep,
                        onLongPressVoice = onSwitchToVoice,
                        modifier = Modifier
                            .weight(2.31f)
                            .fillMaxHeight()
                    )

                    // 5. [。] (1.09f, white)
                    Flypy14PunctKey(
                        label = if (isFlypyChineseMode && !isInSubLayer) "。" else ".",
                        swipeUpBadge = if (showThirdBadge) "3" else null,
                        highlightSwipeUpBadge = showThirdBadge,
                        modifier = Modifier
                            .weight(1.09f)
                            .fillMaxHeight(),
                        onClick = {
                            if (isInSubLayer) {
                                onCommitSecondarySymbol(".")
                            } else {
                                onCommaOrPeriodKey(false)
                            }
                        },
                        onSwipeUp = if (isIdlePeriod) {
                            { onSwipeUpPeriodSlash?.invoke() ?: onCommitChar("/") }
                        } else {
                            null
                        }
                    )

                    // 6. [中/英] (1.39f, grey)
                    val langInteraction = remember { MutableInteractionSource() }
                    val langPressed by langInteraction.collectIsPressedAsState()
                    val effectiveChinese = isFlypyChineseMode && !isInSubLayer
                    Box(
                        modifier = Modifier
                            .weight(1.39f)
                            .fillMaxHeight()
                            .combinedClickable(
                                interactionSource = langInteraction,
                                indication = null,
                                onClick = onToggleFlypyLanguageMode,
                                onLongClick = onSwitchSystemIme
                            )
                            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
                    ) {
                        Flypy14KeycapSurface(
                            isPressed = langPressed,
                            cornerRadius = FLYPY14_KEY_CORNER,
                            faceColor = if (langPressed) palette.keyBorder else palette.keyCapSecondaryBg,
                            borderColor = palette.keyBorder.copy(alpha = 0.50f),
                            shadowColor = palette.keyShadow
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (effectiveChinese) "中" else "EN",
                                    color = palette.keyTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                                Text(
                                    text = if (effectiveChinese) "/英" else "/中",
                                    color = palette.keyTextMuted.copy(alpha = 0.72f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                            }
                        }
                    }

                    // 7. [清除 / 回车] (1.37f, grey when composing or secondary, primary accent when idle primary action)
                    val isPrimaryAction = !hasActiveComposing && enterActionInfo.performEditorActionId != null
                    val enterInteraction = remember { MutableInteractionSource() }
                    val enterPressed by enterInteraction.collectIsPressedAsState()
                    val enterBg = when {
                        isPrimaryAction && enterPressed -> palette.primaryMicSolid.copy(alpha = 0.86f)
                        isPrimaryAction -> palette.primaryMicSolid
                        enterPressed -> palette.keyBorder
                        else -> palette.keyCapSecondaryBg
                    }
                    val enterContentColor = if (isPrimaryAction) palette.onPrimaryMic else palette.keyTextPrimary

                    Box(
                        modifier = Modifier
                            .weight(1.37f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = enterInteraction,
                                indication = null,
                                onClick = {
                                    if (hasActiveComposing) onClearComposing() else onEnterOrNewline()
                                }
                            )
                            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
                    ) {
                        Flypy14KeycapSurface(
                            isPressed = enterPressed,
                            cornerRadius = FLYPY14_KEY_CORNER,
                            faceColor = enterBg,
                            borderColor = if (isPrimaryAction) Color.Transparent else palette.keyBorder.copy(alpha = 0.50f),
                            shadowColor = palette.keyShadow
                        ) {
                            Text(
                                text = if (hasActiveComposing) tr("清除", "Clear", "清除") else enterActionInfo.label,
                                color = enterContentColor,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 2.dp),
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }

        val preview = activeKeyPreview
        if (showKeyPopupPreview && preview != null && rootSizePx.width > 0) {
            Flypy14KeyMagnifierBubble(
                preview = preview,
                rootWidthPx = rootSizePx.width
            )
        }
    }
}

@Composable
private fun Flypy14KeycapSurface(
    isPressed: Boolean,
    cornerRadius: Dp,
    faceColor: Color,
    borderColor: Color,
    shadowColor: Color,
    borderWidth: Dp = 0.55.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val pressTranslatePx = with(density) { 1.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = if (isPressed) pressTranslatePx else 0f
            }
            .clip(shape)
            .background(if (isPressed) Color.Transparent else shadowColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isPressed) 0.dp else FLYPY14_BOTTOM_RIM)
                .clip(shape)
                .background(faceColor)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(borderWidth, borderColor, shape)
                    } else {
                        Modifier
                    }
                ),
            content = content
        )
    }
}

@Composable
private fun Flypy14LetterKey(
    spec: Flypy14KeySpec,
    onCommitToken: (String) -> Unit,
    onCommitTopSymbol: (String) -> Unit,
    onPreviewUpdate: (String, String?, Boolean, Offset, IntSize) -> Long,
    onPreviewEnd: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 18.dp.toPx() }

    var keyPosInWindow by remember { mutableStateOf(Offset.Zero) }
    var keySize by remember { mutableStateOf(IntSize.Zero) }
    var activeGesture by remember { mutableStateOf(Flypy14Gesture.NONE) }

    val currentSpec by rememberUpdatedState(spec)
    val currentOnCommitToken by rememberUpdatedState(onCommitToken)
    val currentOnCommitTopSymbol by rememberUpdatedState(onCommitTopSymbol)
    val currentOnPreviewUpdate by rememberUpdatedState(onPreviewUpdate)
    val currentOnPreviewEnd by rememberUpdatedState(onPreviewEnd)

    fun previewTextFor(dir: Flypy14Gesture): Triple<String, String?, Boolean> {
        return when {
            dir == Flypy14Gesture.UP && currentSpec.topSymbol.isNotEmpty() -> Triple(
                currentSpec.topSymbol,
                null,
                true
            )
            !currentSpec.isDual -> Triple(
                currentSpec.leftChar.uppercaseChar().toString(),
                currentSpec.leftHint,
                false
            )
            dir == Flypy14Gesture.LEFT -> Triple(
                currentSpec.leftChar.uppercaseChar().toString(),
                currentSpec.leftHint,
                true
            )
            dir == Flypy14Gesture.RIGHT -> Triple(
                currentSpec.rightChar!!.uppercaseChar().toString(),
                currentSpec.rightHint,
                true
            )
            else -> Triple(
                "${currentSpec.leftChar.uppercaseChar()} ${currentSpec.rightChar!!.uppercaseChar()}",
                "${currentSpec.leftHint} | ${currentSpec.rightHint}",
                false
            )
        }
    }

    val isPressed = activeGesture != Flypy14Gesture.NONE

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                keyPosInWindow = coords.positionInWindow()
                keySize = coords.size
            }
            .pointerInput(spec.primaryLabel) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startPos = down.position
                    var gesture = Flypy14Gesture.TAP
                    activeGesture = gesture

                    val (initLabel, initHint, initHi) = previewTextFor(gesture)
                    var activePreviewToken = currentOnPreviewUpdate(
                        initLabel,
                        initHint,
                        initHi,
                        keyPosInWindow,
                        keySize
                    )

                    var completedNormally = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = change.position.x - startPos.x
                            val dy = change.position.y - startPos.y
                            val nextGesture = when {
                                currentSpec.topSymbol.isNotEmpty() &&
                                    dy <= -swipeThresholdPx &&
                                    abs(dy) > abs(dx) * 1.1f -> Flypy14Gesture.UP
                                currentSpec.isDual &&
                                    dx <= -swipeThresholdPx &&
                                    abs(dx) >= abs(dy) * 0.85f -> Flypy14Gesture.LEFT
                                currentSpec.isDual &&
                                    dx >= swipeThresholdPx &&
                                    abs(dx) >= abs(dy) * 0.85f -> Flypy14Gesture.RIGHT
                                else -> Flypy14Gesture.TAP
                            }
                            if (nextGesture != gesture) {
                                gesture = nextGesture
                                activeGesture = nextGesture
                                val (lbl, hint, hi) = previewTextFor(nextGesture)
                                activePreviewToken = currentOnPreviewUpdate(
                                    lbl,
                                    hint,
                                    hi,
                                    keyPosInWindow,
                                    keySize
                                )
                            }
                            if (!change.pressed) {
                                change.consume()
                                completedNormally = true
                                break
                            }
                        }
                    } finally {
                        activeGesture = Flypy14Gesture.NONE
                        currentOnPreviewEnd(activePreviewToken)
                    }

                    if (completedNormally) {
                        when {
                            gesture == Flypy14Gesture.UP && currentSpec.topSymbol.isNotEmpty() ->
                                currentOnCommitTopSymbol(currentSpec.topSymbol)
                            !currentSpec.isDual ->
                                currentOnCommitToken(currentSpec.tapToken.toString())
                            gesture == Flypy14Gesture.LEFT ->
                                currentOnCommitToken(currentSpec.leftChar.toString())
                            gesture == Flypy14Gesture.RIGHT ->
                                currentOnCommitToken(currentSpec.rightChar!!.toString())
                            else ->
                                currentOnCommitToken(currentSpec.tapToken.toString())
                        }
                    }
                }
            }
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        val isAccentBorder = activeGesture == Flypy14Gesture.LEFT ||
            activeGesture == Flypy14Gesture.RIGHT ||
            activeGesture == Flypy14Gesture.UP

        Flypy14KeycapSurface(
            isPressed = isPressed,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = if (isAccentBorder) TypeFreeEmerald.copy(alpha = 0.72f) else palette.keyBorder.copy(alpha = 0.50f),
            shadowColor = palette.keyShadow
        ) {
            // Top-center numeric / punctuation symbol matching iFlytek screenshot
            if (spec.topSymbol.isNotEmpty()) {
                val upActive = activeGesture == Flypy14Gesture.UP
                Text(
                    text = spec.topSymbol,
                    color = if (upActive) TypeFreeEmerald else palette.keyTextMuted.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = if (upActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }

            if (spec.isDual) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 11.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftHighlighted = activeGesture == Flypy14Gesture.LEFT
                    val rightHighlighted = activeGesture == Flypy14Gesture.RIGHT

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (leftHighlighted) TypeFreeEmerald.copy(alpha = 0.14f) else Color.Transparent),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = spec.leftChar.uppercaseChar().toString(),
                            color = if (leftHighlighted) TypeFreeEmerald else palette.keyTextPrimary,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = spec.leftHint,
                            color = if (leftHighlighted) TypeFreeEmerald else palette.keyTextMuted.copy(alpha = 0.62f),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (rightHighlighted) TypeFreeEmerald.copy(alpha = 0.14f) else Color.Transparent),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = spec.rightChar!!.uppercaseChar().toString(),
                            color = if (rightHighlighted) TypeFreeEmerald else palette.keyTextPrimary,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = spec.rightHint,
                            color = if (rightHighlighted) TypeFreeEmerald else palette.keyTextMuted.copy(alpha = 0.62f),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 11.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = spec.leftChar.uppercaseChar().toString(),
                        color = palette.keyTextPrimary,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = spec.leftHint,
                        color = palette.keyTextMuted.copy(alpha = 0.62f),
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }
    }
}

@Composable
private fun Flypy14KeyMagnifierBubble(
    preview: Flypy14PopupPreview,
    rootWidthPx: Int
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val bubbleWidthDp = if (preview.hintLabel.isNullOrBlank()) 48.dp else 64.dp
    val bubbleHeightDp = 50.dp
    val bubbleWidthPx = with(density) { bubbleWidthDp.toPx() }
    val bubbleHeightPx = with(density) { bubbleHeightDp.toPx() }
    val overlapPx = with(density) { 6.dp.toPx() }
    val edgeMarginPx = with(density) { 2.dp.roundToPx() }
    val minTopYInRootPx = with(density) { (-41).dp.roundToPx() }

    val maxX = (rootWidthPx - bubbleWidthPx.roundToInt() - edgeMarginPx).coerceAtLeast(edgeMarginPx)
    val offsetX = (preview.centerXInRootPx - bubbleWidthPx / 2f).roundToInt().coerceIn(edgeMarginPx, maxX)
    val offsetY = (preview.keyTopInRootPx - bubbleHeightPx + overlapPx).roundToInt()
        .coerceAtLeast(minTopYInRootPx)

    val bubbleShape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .zIndex(12f)
            .offset { IntOffset(offsetX, offsetY) }
            .size(width = bubbleWidthDp, height = bubbleHeightDp)
            .shadow(elevation = 6.dp, shape = bubbleShape, clip = false)
            .clip(bubbleShape)
            .background(palette.keyPopupBg)
            .border(
                width = if (preview.isHighlighted) 1.2.dp else 0.85.dp,
                color = if (preview.isHighlighted) TypeFreeEmerald else palette.keyBorder,
                shape = bubbleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = preview.primaryLabel,
                color = if (preview.isHighlighted) TypeFreeEmerald else palette.keyTextPrimary,
                fontSize = if (preview.primaryLabel.length > 1) 19.sp else 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            if (!preview.hintLabel.isNullOrBlank()) {
                Text(
                    text = preview.hintLabel,
                    color = palette.keyTextMuted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}

@Composable
private fun Flypy14SimpleKey(
    label: String,
    fontSize: TextUnit,
    onPreviewStart: (String, Offset, IntSize) -> Long,
    onPreviewEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    var keyPosInWindow by remember { mutableStateOf(Offset.Zero) }
    var keySize by remember { mutableStateOf(IntSize.Zero) }
    var isPressed by remember { mutableStateOf(false) }

    val currentLabel by rememberUpdatedState(label)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPreviewStart by rememberUpdatedState(onPreviewStart)
    val currentOnPreviewEnd by rememberUpdatedState(onPreviewEnd)

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                keyPosInWindow = coords.positionInWindow()
                keySize = coords.size
            }
            .pointerInput(label) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    val token = currentOnPreviewStart(currentLabel, keyPosInWindow, keySize)
                    var completed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                completed = true
                                break
                            }
                        }
                    } finally {
                        isPressed = false
                        currentOnPreviewEnd(token)
                    }
                    if (completed) {
                        currentOnClick()
                    }
                }
            }
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        Flypy14KeycapSurface(
            isPressed = isPressed,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = palette.keyBorder.copy(alpha = 0.50f),
            shadowColor = palette.keyShadow
        ) {
            Text(
                text = label,
                color = palette.keyTextPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun Flypy14PunctKey(
    label: String,
    swipeUpBadge: String?,
    highlightSwipeUpBadge: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSwipeUp: (() -> Unit)? = null
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val swipeUpThresholdPx = with(density) { 22.dp.toPx() }
    var isPressed by remember { mutableStateOf(false) }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)

    Box(
        modifier = modifier
            .pointerInput(label, onSwipeUp != null) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startPos = down.position
                    isPressed = true
                    var swipedUp = false
                    var completed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dy = change.position.y - startPos.y
                            if (!swipedUp && currentOnSwipeUp != null && dy <= -swipeUpThresholdPx) {
                                swipedUp = true
                                change.consume()
                                currentOnSwipeUp?.invoke()
                            }
                            if (!change.pressed) {
                                change.consume()
                                completed = true
                                break
                            }
                        }
                    } finally {
                        isPressed = false
                    }
                    if (completed && !swipedUp) {
                        currentOnClick()
                    }
                }
            }
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        Flypy14KeycapSurface(
            isPressed = isPressed,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = palette.keyBorder.copy(alpha = 0.50f),
            shadowColor = palette.keyShadow
        ) {
            if (!swipeUpBadge.isNullOrBlank()) {
                Text(
                    text = swipeUpBadge,
                    color = if (highlightSwipeUpBadge) TypeFreeEmerald else palette.keyTextMuted.copy(alpha = 0.65f),
                    fontSize = 8.5.sp,
                    fontWeight = if (highlightSwipeUpBadge) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )
            }
            Text(
                text = label,
                color = palette.keyTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (!swipeUpBadge.isNullOrBlank()) 2.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun Flypy14SpaceBarKey(
    onTap: () -> Unit,
    onMoveCursorByStep: (Int) -> Unit,
    onLongPressVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val cursorStepPx = with(density) { 10.dp.toPx() }
    val slideStartPx = with(density) { 12.dp.toPx() }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnMoveCursor by rememberUpdatedState(onMoveCursorByStep)
    val currentOnLongPressVoice by rememberUpdatedState(onLongPressVoice)

    var isPressed by remember { mutableStateOf(false) }
    var isCursorSliding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                coroutineScope {
                    val outerScope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startPos = down.position
                        isPressed = true
                        var isSliding = false
                        var longPressed = false
                        var accumulatedDx = 0f
                        var prevX = startPos.x

                        val longPressJob = outerScope.launch {
                            delay(420L)
                            if (!isSliding) {
                                longPressed = true
                                currentOnLongPressVoice()
                            }
                        }

                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val totalDx = change.position.x - startPos.x
                                if (!isSliding && abs(totalDx) >= slideStartPx) {
                                    isSliding = true
                                    isCursorSliding = true
                                    longPressJob.cancel()
                                }
                                if (isSliding) {
                                    val delta = change.position.x - prevX
                                    accumulatedDx += delta
                                    val steps = (accumulatedDx / cursorStepPx).toInt()
                                    if (steps != 0) {
                                        currentOnMoveCursor(steps)
                                        accumulatedDx -= steps * cursorStepPx
                                    }
                                    change.consume()
                                }
                                prevX = change.position.x
                                if (!change.pressed) {
                                    change.consume()
                                    completed = true
                                    break
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            isPressed = false
                            isCursorSliding = false
                        }

                        if (completed && !isSliding && !longPressed) {
                            currentOnTap()
                        }
                    }
                }
            }
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        Flypy14KeycapSurface(
            isPressed = isPressed,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = palette.keyBorder.copy(alpha = 0.50f),
            shadowColor = palette.keyShadow
        ) {
            if (isCursorSliding) {
                Text(
                    text = tr("◀ 光标 ▶", "◀ Cursor ▶", "◀ 游標 ▶"),
                    color = palette.keyTextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // iFlytek-style Mic capsule + U-cup + stem centered above rounded spacebar bracket
                val iconColor = palette.keyTextPrimary.copy(alpha = 0.78f)
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 36.dp, height = 22.dp)
                ) {
                    val strokePx = 1.55.dp.toPx()
                    val cx = size.width / 2f

                    // Mic capsule
                    val capW = 5.4.dp.toPx()
                    val capH = 8.4.dp.toPx()
                    val capTop = 1.dp.toPx()
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(cx - capW / 2f, capTop),
                        size = Size(capW, capH),
                        cornerRadius = CornerRadius(capW / 2f, capW / 2f),
                        style = Stroke(width = strokePx)
                    )

                    // Mic U-cup arc
                    val cupW = 9.4.dp.toPx()
                    val cupH = 7.4.dp.toPx()
                    val cupTop = capTop + 3.4.dp.toPx()
                    drawArc(
                        color = iconColor,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - cupW / 2f, cupTop),
                        size = Size(cupW, cupH),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Mic stem
                    val cupBottom = cupTop + cupH
                    val stemBottom = cupBottom + 2.2.dp.toPx()
                    drawLine(
                        color = iconColor,
                        start = Offset(cx, cupBottom),
                        end = Offset(cx, stemBottom),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )

                    // Spacebar bracket └────────┘
                    val bLeft = 2.dp.toPx()
                    val bRight = size.width - 2.dp.toPx()
                    val bTop = stemBottom + 0.5.dp.toPx()
                    val bBottom = size.height - 1.5.dp.toPx()
                    val r = 2.2.dp.toPx()
                    val bracketPath = Path().apply {
                        moveTo(bLeft, bTop)
                        lineTo(bLeft, bBottom - r)
                        quadraticTo(bLeft, bBottom, bLeft + r, bBottom)
                        lineTo(bRight - r, bBottom)
                        quadraticTo(bRight, bBottom, bRight, bBottom - r)
                        lineTo(bRight, bTop)
                    }
                    drawPath(
                        path = bracketPath,
                        color = iconColor,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

@Composable
private fun Flypy14FunctionKey(
    label: String,
    fontSize: TextUnit = 13.5.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    isActiveHighlight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        Flypy14KeycapSurface(
            isPressed = isPressed,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = when {
                isPressed -> palette.keyBorder
                isActiveHighlight -> palette.capsuleSelectedBg
                else -> palette.keyCapSecondaryBg
            },
            borderColor = if (isActiveHighlight) TypeFreeEmerald.copy(alpha = 0.65f) else palette.keyBorder.copy(alpha = 0.50f),
            shadowColor = palette.keyShadow
        ) {
            Text(
                text = label,
                color = if (isActiveHighlight) TypeFreeEmerald else palette.keyTextPrimary,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

@Composable
private fun Flypy14RepeatingBackspaceKey(
    onBackspace: () -> Unit,
    onSwipeUpAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val swipeUpThresholdPx = with(density) { 24.dp.toPx() }

    val currentOnBackspace by rememberUpdatedState(onBackspace)
    val currentOnSwipeUpAction by rememberUpdatedState(onSwipeUpAction)
    var isHolding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                coroutineScope {
                    val outerScope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startPos = down.position
                        isHolding = true
                        var swipedUp = false
                        var repeatStarted = false

                        val repeatJob = outerScope.launch {
                            delay(320L)
                            if (!swipedUp) {
                                repeatStarted = true
                                while (isActive && !swipedUp) {
                                    currentOnBackspace()
                                    delay(48L)
                                }
                            }
                        }
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dy = change.position.y - startPos.y
                                if (!repeatStarted && !swipedUp && dy <= -swipeUpThresholdPx) {
                                    swipedUp = true
                                    repeatJob.cancel()
                                    change.consume()
                                    currentOnSwipeUpAction()
                                }
                                if (!change.pressed) {
                                    change.consume()
                                    completed = true
                                    break
                                }
                            }
                        } finally {
                            isHolding = false
                            repeatJob.cancel()
                        }

                        if (completed && !swipedUp && !repeatStarted) {
                            currentOnBackspace()
                        }
                    }
                }
            }
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        Flypy14KeycapSurface(
            isPressed = isHolding,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = if (isHolding) palette.keyBorder else palette.keyCapSecondaryBg,
            borderColor = palette.keyBorder.copy(alpha = 0.50f),
            shadowColor = palette.keyShadow
        ) {
            Icon(
                imageVector = TypeFreeIcons.Backspace,
                contentDescription = "退格删除",
                tint = palette.keyTextPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp)
            )
        }
    }
}

/**
 * Compact host adapter keeping `KeyboardScreen.kt` concise and matching `BuiltInT9KeyboardHost`.
 */
@Composable
fun BuiltInFlypy14KeyboardHost(
    uiState: ImeUiState,
    showKeyPopupPreview: Boolean,
    commaPeriodQuickSelect: Boolean,
    onAction: (ImeUiAction) -> Unit,
    backspaceSwipeUpClearEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    BuiltInFlypy14Keyboard(
        enterActionInfo = uiState.enterActionInfo,
        isFlypyChineseMode = uiState.isFlypyChineseMode,
        flypyComposingCode = uiState.flypyComposingCode,
        flypyCandidateCount = uiState.flypyCandidates.size,
        commaPeriodQuickSelect = commaPeriodQuickSelect,
        onCommitChar = { onAction(ImeUiAction.CommitQwertyChar(it)) },
        onCommitSecondarySymbol = { onAction(ImeUiAction.CommitSecondarySymbol(it)) },
        onMoveCursorByStep = { onAction(ImeUiAction.MoveCursorByChars(it)) },
        onCommaOrPeriodKey = { isComma -> onAction(ImeUiAction.CommaOrPeriodKey(isComma)) },
        onInsertSpace = { onAction(ImeUiAction.InsertSpace) },
        onToggleFlypyLanguageMode = { onAction(ImeUiAction.ToggleFlypyLanguageMode) },
        onClearComposing = { onAction(ImeUiAction.ClearFlypyComposing) },
        onFlushComposingBeforeLayerSwitch = { onAction(ImeUiAction.FlushFlypyComposing) },
        onBackspace = { onAction(ImeUiAction.Backspace) },
        onSwipeUpBackspaceUndo = { onAction(ImeUiAction.TriggerUndo) },
        backspaceSwipeUpClearEnabled = backspaceSwipeUpClearEnabled,
        onSwipeUpBackspaceClear = { onAction(ImeUiAction.ClearInputBox) },
        onEnterOrNewline = { onAction(ImeUiAction.EnterOrNewline) },
        onSwitchToVoice = { onAction(ImeUiAction.SelectTab(KeyboardTabMode.VOICE)) },
        onSwitchSystemIme = { onAction(ImeUiAction.SwitchKeyboard) },
        inputSessionToken = uiState.inputSessionToken,
        showKeyPopupPreview = showKeyPopupPreview,
        onSwipeUpPeriodSlash = { onAction(ImeUiAction.SwipeUpPeriodSlash) },
        modifier = modifier
    )
}
