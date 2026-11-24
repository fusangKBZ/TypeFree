package com.typefree.ime.ime

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

private enum class Flypy14SwipeDirection {
    NONE,
    TAP,
    LEFT,
    RIGHT
}

private data class Flypy14PopupPreview(
    val token: Long,
    val primaryLabel: String,
    val hintLabel: String?,
    val isExactSwipe: Boolean,
    val centerXInRootPx: Float,
    val keyTopInRootPx: Float
)

private val FLYPY14_NUM_ROW_1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val FLYPY14_NUM_ROW_2 = listOf("@", "#", "$", "%", "*", "-", "+", "(", ")", "/")
private val FLYPY14_NUM_ROW_3 = listOf(";", "=", "？", "！", ":", "\"", "'")

private val FLYPY14_SYM_ROW_1 = listOf("[", "]", "{", "}", "<", ">", "^", "&", "=", "~")
private val FLYPY14_SYM_ROW_2 = listOf("_", "\\", "|", "、", "；", "“", "”", "‘", "’", "…")
private val FLYPY14_SYM_ROW_3 = listOf("¥", "€", "£", "°", "•", "`", "?")

private val FLYPY14_CELL_HPAD = 2.4.dp
private val FLYPY14_CELL_VPAD = 3.2.dp
private val FLYPY14_KEY_CORNER = 6.5.dp
private val FLYPY14_FUNC_CORNER = 7.5.dp
private val FLYPY14_BOTTOM_RIM = 1.25.dp

/**
 * Built-in 14-key QWERTY-merged Xiaohe Shuangpin keyboard (`double_pinyin_flypy_14`):
 * - Full-width 5-column layout without a left sidebar (`QW ER TY UI OP / AS DF GH JK L / ZX CV BN M ⌫`).
 * - Every letter keycap displays compact Xiaohe final / special-initial hints underneath.
 * - Tap sends the ambiguous dual-key token (`Q E T U O A D G J Z C B`) for Rime full-sentence disambiguation;
 *   horizontal swipe left/right sends exact lowercase left/right letters; solo `L`/`M` send exact `l`/`m`.
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
    onFlushComposingBeforeLayerSwitch: () -> Unit,
    onBackspace: () -> Unit,
    onSwipeUpBackspaceUndo: () -> Unit,
    backspaceSwipeUpClearEnabled: Boolean = false,
    onSwipeUpBackspaceClear: () -> Unit = {},
    onEnterOrNewline: () -> Unit,
    onSwitchSystemIme: () -> Unit,
    inputSessionToken: Int = 0,
    showKeyPopupPreview: Boolean = true,
    onSwipeUpPeriodSlash: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    var isSymbolLayer by remember { mutableStateOf(false) }
    var isMoreSymbols by remember { mutableStateOf(false) }

    var rootPosInWindow by remember { mutableStateOf(Offset.Zero) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var previewSeq by remember { mutableLongStateOf(0L) }
    var activeKeyPreview by remember { mutableStateOf<Flypy14PopupPreview?>(null) }

    val hasActiveComposing = flypyComposingCode.isNotEmpty()

    LaunchedEffect(inputSessionToken) {
        isSymbolLayer = false
        isMoreSymbols = false
        activeKeyPreview = null
    }

    fun showPreviewForKey(
        primaryLabel: String,
        hintLabel: String?,
        isExactSwipe: Boolean,
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
            isExactSwipe = isExactSwipe,
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
                if (!isSymbolLayer) {
                    val rows = Flypy14KeyHelper.KEY_ROWS
                    // Row 1: QW ER TY UI OP (5 equal keys)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        rows[0].forEach { spec ->
                            Flypy14LetterKey(
                                spec = spec,
                                onCommitToken = onCommitChar,
                                onPreviewUpdate = ::showPreviewForKey,
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    // Row 2: AS DF GH JK L (5 equal keys)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        rows[1].forEach { spec ->
                            Flypy14LetterKey(
                                spec = spec,
                                onCommitToken = onCommitChar,
                                onPreviewUpdate = ::showPreviewForKey,
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    // Row 3: ZX CV BN M ⌫ (5 equal keys)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        rows[2].forEach { spec ->
                            Flypy14LetterKey(
                                spec = spec,
                                onCommitToken = onCommitChar,
                                onPreviewUpdate = ::showPreviewForKey,
                                onPreviewEnd = ::clearPreviewToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        Flypy14RepeatingBackspaceKey(
                            onBackspace = onBackspace,
                            onSwipeUpAction = if (backspaceSwipeUpClearEnabled) onSwipeUpBackspaceClear else onSwipeUpBackspaceUndo,
                            swipeUpBadge = if (backspaceSwipeUpClearEnabled) tr("清", "Clr", "清") else tr("撤", "Undo", "撤"),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    val row1 = if (isMoreSymbols) FLYPY14_SYM_ROW_1 else FLYPY14_NUM_ROW_1
                    val row2 = if (isMoreSymbols) FLYPY14_SYM_ROW_2 else FLYPY14_NUM_ROW_2
                    val row3 = if (isMoreSymbols) FLYPY14_SYM_ROW_3 else FLYPY14_NUM_ROW_3

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        row1.forEach { sym ->
                            Flypy14SimpleKey(
                                label = sym,
                                fontSize = 17.5.sp,
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
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        row2.forEach { sym ->
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
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Flypy14FunctionKey(
                            label = if (isMoreSymbols) "123" else "#+=",
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight(),
                            onClick = { isMoreSymbols = !isMoreSymbols }
                        )

                        row3.forEach { sym ->
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

                        Flypy14RepeatingBackspaceKey(
                            onBackspace = onBackspace,
                            onSwipeUpAction = if (backspaceSwipeUpClearEnabled) onSwipeUpBackspaceClear else onSwipeUpBackspaceUndo,
                            swipeUpBadge = if (backspaceSwipeUpClearEnabled) tr("清", "Clr", "清") else tr("撤", "Undo", "撤"),
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight()
                        )
                    }
                }

                // Row 4: Full-width 6-key bottom row matching BuiltInQwertyKeyboard (total = 10.00f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.04f),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Flypy14FunctionKey(
                        label = if (isSymbolLayer) "ABC" else "123",
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                        onClick = {
                            if (!isSymbolLayer && hasActiveComposing) {
                                onFlushComposingBeforeLayerSwitch()
                            }
                            isSymbolLayer = !isSymbolLayer
                            isMoreSymbols = false
                        }
                    )

                    // [中/英] Toggle Key
                    val langInteraction = remember { MutableInteractionSource() }
                    val langPressed by langInteraction.collectIsPressedAsState()
                    val effectiveChinese = isFlypyChineseMode && !isSymbolLayer
                    Box(
                        modifier = Modifier
                            .weight(1.00f)
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
                            cornerRadius = FLYPY14_FUNC_CORNER,
                            faceColor = if (langPressed) palette.keyBorder else palette.keyCapSecondaryBg,
                            borderColor = palette.keyBorder.copy(alpha = 0.65f),
                            shadowColor = palette.keyShadow
                        ) {
                            Text(
                                text = if (effectiveChinese) "中" else "EN",
                                color = palette.keyTextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = (-2).dp, y = (-1).dp)
                            )
                            Text(
                                text = if (effectiveChinese) "英" else "中",
                                color = palette.keyTextMuted.copy(alpha = 0.62f),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 2.dp, end = 4.dp)
                            )
                        }
                    }

                    val showSecondBadge = hasActiveComposing && commaPeriodQuickSelect && flypyCandidateCount >= 2
                    val showThirdBadge = hasActiveComposing && commaPeriodQuickSelect && flypyCandidateCount >= 3
                    val isIdleChineseComma = isFlypyChineseMode && !isSymbolLayer && !hasActiveComposing
                    val isIdlePeriod = !isSymbolLayer && !hasActiveComposing

                    Flypy14PunctKey(
                        label = if (isFlypyChineseMode && !isSymbolLayer) "，" else ",",
                        swipeUpBadge = when {
                            showSecondBadge -> "2"
                            isIdleChineseComma -> ";"
                            else -> null
                        },
                        highlightSwipeUpBadge = showSecondBadge,
                        modifier = Modifier
                            .weight(1.00f)
                            .fillMaxHeight(),
                        onClick = {
                            if (isSymbolLayer) {
                                onCommitSecondarySymbol(",")
                            } else {
                                onCommaOrPeriodKey(true)
                            }
                        },
                        onSwipeUp = if (isIdleChineseComma) ({ onCommitChar(";") }) else null
                    )

                    Flypy14SpaceBarKey(
                        hasComposing = hasActiveComposing,
                        isChineseMode = isFlypyChineseMode && !isSymbolLayer,
                        onTap = onInsertSpace,
                        onMoveCursorByStep = onMoveCursorByStep,
                        onLongPressToggleLanguage = onToggleFlypyLanguageMode,
                        modifier = Modifier
                            .weight(3.95f)
                            .fillMaxHeight()
                    )

                    Flypy14PunctKey(
                        label = if (isFlypyChineseMode && !isSymbolLayer) "。" else ".",
                        swipeUpBadge = when {
                            isSymbolLayer -> null
                            showThirdBadge -> "3"
                            isIdlePeriod && isFlypyChineseMode -> "/"
                            else -> null
                        },
                        highlightSwipeUpBadge = showThirdBadge,
                        modifier = Modifier
                            .weight(1.00f)
                            .fillMaxHeight(),
                        onClick = {
                            if (isSymbolLayer) {
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
                            .weight(1.70f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = enterInteraction,
                                indication = null,
                                onClick = onEnterOrNewline
                            )
                            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
                    ) {
                        Flypy14KeycapSurface(
                            isPressed = enterPressed,
                            cornerRadius = FLYPY14_FUNC_CORNER,
                            faceColor = enterBg,
                            borderColor = if (isPrimaryAction) Color.Transparent else palette.keyBorder.copy(alpha = 0.65f),
                            shadowColor = palette.keyShadow
                        ) {
                            if (hasActiveComposing) {
                                Text(
                                    text = tr("提交", "Done", "提交"),
                                    color = enterContentColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (!isPrimaryAction) {
                                        Icon(
                                            imageVector = TypeFreeIcons.ReturnEnter,
                                            contentDescription = enterActionInfo.label,
                                            tint = enterContentColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Text(
                                        text = enterActionInfo.label,
                                        color = enterContentColor,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
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
    borderWidth: Dp = 0.65.dp,
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
    onPreviewUpdate: (String, String?, Boolean, Offset, IntSize) -> Long,
    onPreviewEnd: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 18.dp.toPx() }

    var keyPosInWindow by remember { mutableStateOf(Offset.Zero) }
    var keySize by remember { mutableStateOf(IntSize.Zero) }
    var swipeDir by remember { mutableStateOf(Flypy14SwipeDirection.NONE) }

    val currentSpec by rememberUpdatedState(spec)
    val currentOnCommitToken by rememberUpdatedState(onCommitToken)
    val currentOnPreviewUpdate by rememberUpdatedState(onPreviewUpdate)
    val currentOnPreviewEnd by rememberUpdatedState(onPreviewEnd)

    fun previewTextFor(dir: Flypy14SwipeDirection): Triple<String, String?, Boolean> {
        return when {
            !currentSpec.isDual -> Triple(
                currentSpec.leftChar.uppercaseChar().toString(),
                currentSpec.leftHint,
                false
            )
            dir == Flypy14SwipeDirection.LEFT -> Triple(
                currentSpec.leftChar.uppercaseChar().toString(),
                currentSpec.leftHint,
                true
            )
            dir == Flypy14SwipeDirection.RIGHT -> Triple(
                currentSpec.rightChar!!.uppercaseChar().toString(),
                currentSpec.rightHint,
                true
            )
            else -> Triple(
                currentSpec.primaryLabel,
                "${currentSpec.leftHint} | ${currentSpec.rightHint}",
                false
            )
        }
    }

    val isPressed = swipeDir != Flypy14SwipeDirection.NONE

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
                    var activeDir = Flypy14SwipeDirection.TAP
                    swipeDir = activeDir

                    val (initLabel, initHint, initExact) = previewTextFor(activeDir)
                    var activePreviewToken = currentOnPreviewUpdate(
                        initLabel,
                        initHint,
                        initExact,
                        keyPosInWindow,
                        keySize
                    )

                    var completedNormally = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (currentSpec.isDual) {
                                val dx = change.position.x - startPos.x
                                val dy = change.position.y - startPos.y
                                val nextDir = when {
                                    dx <= -swipeThresholdPx && abs(dx) >= abs(dy) * 0.85f -> Flypy14SwipeDirection.LEFT
                                    dx >= swipeThresholdPx && abs(dx) >= abs(dy) * 0.85f -> Flypy14SwipeDirection.RIGHT
                                    else -> Flypy14SwipeDirection.TAP
                                }
                                if (nextDir != activeDir) {
                                    activeDir = nextDir
                                    swipeDir = nextDir
                                    val (lbl, hint, exact) = previewTextFor(nextDir)
                                    activePreviewToken = currentOnPreviewUpdate(
                                        lbl,
                                        hint,
                                        exact,
                                        keyPosInWindow,
                                        keySize
                                    )
                                }
                            }
                            if (!change.pressed) {
                                change.consume()
                                completedNormally = true
                                break
                            }
                        }
                    } finally {
                        swipeDir = Flypy14SwipeDirection.NONE
                        currentOnPreviewEnd(activePreviewToken)
                    }

                    if (completedNormally) {
                        val emitted = when {
                            !currentSpec.isDual -> currentSpec.tapToken.toString()
                            activeDir == Flypy14SwipeDirection.LEFT -> currentSpec.leftChar.toString()
                            activeDir == Flypy14SwipeDirection.RIGHT -> currentSpec.rightChar!!.toString()
                            else -> currentSpec.tapToken.toString()
                        }
                        currentOnCommitToken(emitted)
                    }
                }
            }
            .padding(horizontal = FLYPY14_CELL_HPAD, vertical = FLYPY14_CELL_VPAD)
    ) {
        Flypy14KeycapSurface(
            isPressed = isPressed,
            cornerRadius = FLYPY14_KEY_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = if (swipeDir == Flypy14SwipeDirection.LEFT || swipeDir == Flypy14SwipeDirection.RIGHT) {
                TypeFreeEmerald.copy(alpha = 0.72f)
            } else {
                palette.keyBorder.copy(alpha = 0.65f)
            },
            shadowColor = palette.keyShadow
        ) {
            if (spec.isDual) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftHighlighted = swipeDir == Flypy14SwipeDirection.LEFT
                    val rightHighlighted = swipeDir == Flypy14SwipeDirection.RIGHT
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (leftHighlighted) TypeFreeEmerald.copy(alpha = 0.16f) else Color.Transparent),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = spec.leftChar.uppercaseChar().toString(),
                            color = if (leftHighlighted) TypeFreeEmerald else palette.keyTextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = spec.leftHint,
                            color = if (leftHighlighted) TypeFreeEmerald else palette.keyTextMuted,
                            fontSize = if (spec.leftHint.length > 4) 7.5.sp else 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(22.dp)
                            .background(palette.keyBorder.copy(alpha = 0.38f))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (rightHighlighted) TypeFreeEmerald.copy(alpha = 0.16f) else Color.Transparent),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = spec.rightChar!!.uppercaseChar().toString(),
                            color = if (rightHighlighted) TypeFreeEmerald else palette.keyTextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = spec.rightHint,
                            color = if (rightHighlighted) TypeFreeEmerald else palette.keyTextMuted,
                            fontSize = if (spec.rightHint.length > 4) 7.5.sp else 8.5.sp,
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
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = spec.leftChar.uppercaseChar().toString(),
                        color = palette.keyTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = spec.leftHint,
                        color = palette.keyTextMuted,
                        fontSize = if (spec.leftHint.length > 5) 7.5.sp else 8.5.sp,
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
                width = if (preview.isExactSwipe) 1.2.dp else 0.85.dp,
                color = if (preview.isExactSwipe) TypeFreeEmerald else palette.keyBorder,
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
                color = if (preview.isExactSwipe) TypeFreeEmerald else palette.keyTextPrimary,
                fontSize = if (preview.primaryLabel.length > 1) 20.sp else 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            if (!preview.hintLabel.isNullOrBlank()) {
                Text(
                    text = preview.hintLabel,
                    color = palette.keyTextMuted,
                    fontSize = 9.sp,
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
            borderColor = palette.keyBorder.copy(alpha = 0.65f),
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
            cornerRadius = FLYPY14_FUNC_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = palette.keyBorder.copy(alpha = 0.65f),
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
                fontSize = 17.5.sp,
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
    hasComposing: Boolean,
    isChineseMode: Boolean,
    onTap: () -> Unit,
    onMoveCursorByStep: (Int) -> Unit,
    onLongPressToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val density = LocalDensity.current
    val cursorStepPx = with(density) { 10.dp.toPx() }
    val slideStartPx = with(density) { 12.dp.toPx() }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnMoveCursor by rememberUpdatedState(onMoveCursorByStep)
    val currentOnLongPressLang by rememberUpdatedState(onLongPressToggleLanguage)

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
                                currentOnLongPressLang()
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
            cornerRadius = FLYPY14_FUNC_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapBg,
            borderColor = palette.keyBorder.copy(alpha = 0.65f),
            shadowColor = palette.keyShadow
        ) {
            val label = when {
                isCursorSliding -> tr("◀ 光标移动 ▶", "◀ Move Cursor ▶", "◀ 游標移動 ▶")
                hasComposing -> tr("选定首项", "Select", "選定首項")
                isChineseMode -> tr("小鹤 14 键", "Flypy 14", "小鶴 14 鍵")
                else -> "English"
            }
            Text(
                text = label,
                color = if (hasComposing || isCursorSliding) palette.keyTextPrimary else palette.keyTextMuted,
                fontSize = 12.sp,
                fontWeight = if (hasComposing || isCursorSliding) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun Flypy14FunctionKey(
    label: String,
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
            cornerRadius = FLYPY14_FUNC_CORNER,
            faceColor = if (isPressed) palette.keyBorder else palette.keyCapSecondaryBg,
            borderColor = palette.keyBorder.copy(alpha = 0.65f),
            shadowColor = palette.keyShadow
        ) {
            Text(
                text = label,
                color = palette.keyTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun Flypy14RepeatingBackspaceKey(
    onBackspace: () -> Unit,
    onSwipeUpAction: () -> Unit,
    swipeUpBadge: String = "撤",
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
            cornerRadius = FLYPY14_FUNC_CORNER,
            faceColor = if (isHolding) palette.keyBorder else palette.keyCapSecondaryBg,
            borderColor = palette.keyBorder.copy(alpha = 0.65f),
            shadowColor = palette.keyShadow
        ) {
            Text(
                text = swipeUpBadge,
                color = palette.keyTextMuted.copy(alpha = 0.56f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.5.dp, end = 3.5.dp)
            )
            Icon(
                imageVector = TypeFreeIcons.Backspace,
                contentDescription = if (swipeUpBadge == "清") "退格删除（上划清空输入框）" else "退格删除（上划撤回上屏）",
                tint = palette.keyTextPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(19.dp)
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
        onFlushComposingBeforeLayerSwitch = { onAction(ImeUiAction.FlushFlypyComposing) },
        onBackspace = { onAction(ImeUiAction.Backspace) },
        onSwipeUpBackspaceUndo = { onAction(ImeUiAction.TriggerUndo) },
        backspaceSwipeUpClearEnabled = backspaceSwipeUpClearEnabled,
        onSwipeUpBackspaceClear = { onAction(ImeUiAction.ClearInputBox) },
        onEnterOrNewline = { onAction(ImeUiAction.EnterOrNewline) },
        onSwitchSystemIme = { onAction(ImeUiAction.SwitchKeyboard) },
        inputSessionToken = uiState.inputSessionToken,
        showKeyPopupPreview = showKeyPopupPreview,
        onSwipeUpPeriodSlash = { onAction(ImeUiAction.SwipeUpPeriodSlash) },
        modifier = modifier
    )
}
