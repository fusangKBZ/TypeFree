package com.typefree.ime.ime.candidates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typefree.ime.data.InputScheme
import com.typefree.ime.ime.ImeLogic
import com.typefree.ime.ime.LocalKeyboardMetrics
import com.typefree.ime.ime.chrome.TopBarAskAiPill
import com.typefree.ime.ime.chrome.TopBarToolPanelButton
import com.typefree.ime.ime.flypy.FlypyCandidate
import com.typefree.ime.ui.components.TypeFreeIcons
import com.typefree.ime.ui.theme.LocalTypeFreeKeyboardPalette
import com.typefree.ime.ui.theme.TypeFreeEmerald
import com.typefree.ime.ui.theme.TypeFreeRose

/**
 * 34.dp Prediction & AI Completion Strip reusing the Top Bar slot:
 * `[28.dp Expand Button ⊞] [Scrollable L1/L2 Prediction Chips...] [✦ AI Completion Chip (<=12 chars)] [×]`
 */
@Composable
fun PredictionCandidateBar(
    isToolPanelOpen: Boolean,
    predictions: List<FlypyCandidate>,
    aiCompletion: String?,
    onToggleToolPanel: () -> Unit,
    onSelectPrediction: (Int) -> Unit,
    onSelectAiCompletion: (String) -> Unit,
    onDismiss: () -> Unit,
    passiveMathResult: String? = null,
    onSelectPassiveMath: ((String) -> Unit)? = null,
    phraseSuggestionLabel: String? = null,
    onAcceptPhraseSuggestion: (() -> Unit)? = null,
    onDismissPhraseSuggestion: (() -> Unit)? = null,
    englishSuggestions: List<String> = emptyList(),
    onSelectEnglishSuggestion: ((String) -> Unit)? = null,
    showAskAiPill: Boolean = false,
    isAskAiFollowUp: Boolean = false,
    onOpenAskAiCard: () -> Unit = {}
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val metrics = LocalKeyboardMetrics.current
    val scrollState = rememberScrollState()
    val emeraldAccentText = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Stationary 34.dp Settings Gear Button (identical position to TypeFreeTopBar)
        TopBarToolPanelButton(
            isOpen = isToolPanelOpen,
            onClick = onToggleToolPanel
        )

        // 2. Scrollable Passive Math / L2 Phrase / EN Suggestions / L1-L2 Prediction Chips
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cleanMath = passiveMathResult?.trim().orEmpty()
            if (cleanMath.isNotEmpty()) {
                val mathDisplay = if (cleanMath.startsWith("=")) cleanMath else "= $cleanMath"
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(TypeFreeEmerald.copy(alpha = if (palette.isDark) 0.16f else 0.12f))
                        .border(1.dp, TypeFreeEmerald.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
                        .clickable { onSelectPassiveMath?.invoke(cleanMath) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mathDisplay,
                        color = emeraldAccentText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            val cleanPhraseLabel = phraseSuggestionLabel?.trim().orEmpty()
            if (cleanPhraseLabel.isNotEmpty()) {
                val displayPhrase = if (
                    cleanPhraseLabel.startsWith("+") ||
                    cleanPhraseLabel.startsWith("录入")
                ) {
                    cleanPhraseLabel
                } else {
                    "+ 录入: $cleanPhraseLabel"
                }
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.capsuleSelectedBg)
                        .border(1.dp, TypeFreeEmerald.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
                        .padding(start = 10.dp, end = if (onDismissPhraseSuggestion != null) 4.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayPhrase,
                        color = emeraldAccentText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.clickable { onAcceptPhraseSuggestion?.invoke() }
                    )
                    if (onDismissPhraseSuggestion != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.keyCapSecondaryBg.copy(alpha = 0.72f))
                                .clickable(onClick = onDismissPhraseSuggestion),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "×",
                                color = palette.keyTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            englishSuggestions.forEach { word ->
                if (word.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.keyCapBg)
                            .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                            .clickable { onSelectEnglishSuggestion?.invoke(word) }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = word,
                            color = palette.keyTextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            predictions.forEachIndexed { idx, cand ->
                val isFirstPrediction = idx == 0
                val visualText = if (cand.text.length > 16 || cand.text.contains('\n')) {
                    cand.text.replace('\n', '↵').take(16) + "…"
                } else {
                    cand.text
                }
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isFirstPrediction) palette.capsuleSelectedBg else palette.keyCapBg)
                        .border(
                            width = 1.dp,
                            color = if (isFirstPrediction) TypeFreeEmerald.copy(alpha = 0.45f) else palette.keyBorder,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .clickable { onSelectPrediction(idx) }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = visualText,
                        color = palette.keyTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isFirstPrediction) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        // 3. Pinned Right-Side L3 AI Completion Chip (TypeFreeEmerald border + `✦` prefix + max 12 chars)
        val cleanAiText = aiCompletion?.trim().orEmpty()
        if (cleanAiText.isNotEmpty()) {
            val singleLineAi = cleanAiText.replace('\n', '↵')
            val truncatedAi = if (singleLineAi.length > 12) {
                singleLineAi.take(12) + "…"
            } else {
                singleLineAi
            }
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(TypeFreeEmerald.copy(alpha = if (palette.isDark) 0.16f else 0.12f))
                    .border(1.dp, TypeFreeEmerald.copy(alpha = 0.72f), RoundedCornerShape(9.dp))
                    .clickable { onSelectAiCompletion(cleanAiText) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✦ $truncatedAi",
                    color = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        if (showAskAiPill) {
            TopBarAskAiPill(onClick = onOpenAskAiCard, isFollowUp = isAskAiFollowUp)
        }

        // 4. Dismiss Predictions (`×`)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(palette.keyCapSecondaryBg)
                .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
            .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = TypeFreeIcons.Close,
                contentDescription = "关闭联想词",
                tint = palette.keyTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Lightweight Floating Preedit Capsule rendered in the `28.dp` transparent overhang right above
 * the keyboard's `1.dp` top divider (zero host viewport resize via `contentTopInsets`):
 * - Displays segmented pinyin / Flypy code (`yz`, `wo'men'ming'tian`) in a crisp `22.dp` elevated pill
 *   floating `4.dp` above the keyboard top edge.
 * - Frees the `34.dp` top bar slot inside the keyboard for a spacious, uncramped single-row candidate strip
 *   while keeping Candidate #1 permanently riveted at `X = 12.dp` (`0px` horizontal shift).
 */
@Composable
fun FloatingPreeditCapsule(
    composingCode: String,
    preeditDisplay: String = composingCode,
    inputScheme: InputScheme = InputScheme.FLYPY_TABLE
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val displayPreedit = remember(preeditDisplay, composingCode, inputScheme) {
        ImeLogic.formatPreeditDisplay(
            preedit = preeditDisplay,
            rawCode = composingCode,
            scheme = inputScheme,
            maxVisibleChars = 38
        )
    }
    if (displayPreedit.isBlank()) return

    val pillShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .height(22.dp)
            .shadow(elevation = 2.dp, shape = pillShape, clip = false)
            .clip(pillShape)
            .background(palette.keyCapBg)
            .border(1.dp, palette.keyBorder, pillShape)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = displayPreedit,
            style = TextStyle(
                color = palette.keyTextPrimary,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (inputScheme.usesRime) FontFamily.Default else FontFamily.Monospace,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(TypeFreeEmerald)
        )
    }
}

/**
 * Dedicated top-row Pinyin selector bar above the 1.dp keyboard divider for 14-key Xiaohe:
 * displays locked syllables (`[zhong ×]` clickable to unlock/re-pick) + remaining syllable options
 * (`guo`, `huo`, `luo`...) across the full width so the main candidate row below is 100% words.
 */
@Composable
fun Flypy14PinyinOverhangBar(
    lockedSyllables: List<String>,
    syllableOptions: List<String>,
    onSelectSyllable: (String) -> Unit,
    onUnlockSyllable: (Int) -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val emeraldText = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
    val rowScroll = rememberScrollState()
    LaunchedEffect(lockedSyllables, syllableOptions) {
        rowScroll.scrollTo(0)
    }

    val barShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .shadow(elevation = 2.dp, shape = barShape, clip = false)
            .clip(barShape)
            .background(palette.keyboardCanvas)
            .border(1.dp, palette.keyBorder, barShape)
            .horizontalScroll(rowScroll)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lockedSyllables.forEachIndexed { idx, syl ->
            Row(
                modifier = Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.capsuleSelectedBg)
                    .border(1.dp, TypeFreeEmerald.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
                    .clickable { onUnlockSyllable(idx) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = syl,
                    style = TextStyle(
                        color = emeraldText,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1
                )
                Text(
                    text = "×",
                    style = TextStyle(
                        color = emeraldText.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1
                )
            }
        }

        if (lockedSyllables.isNotEmpty() && syllableOptions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(palette.keyBorder)
            )
        }

        syllableOptions.forEachIndexed { idx, syl ->
            val isLead = lockedSyllables.isEmpty() && idx == 0
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isLead) palette.capsuleSelectedBg else palette.keyCapBg)
                    .border(
                        width = 1.dp,
                        color = if (isLead) TypeFreeEmerald.copy(alpha = 0.45f) else palette.keyBorder,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable { onSelectSyllable(syl) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = syl,
                    style = TextStyle(
                        color = if (isLead) emeraldText else palette.keyTextPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (isLead) FontWeight.Bold else FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1
                )
            }
        }

        if (syllableOptions.isEmpty() && lockedSyllables.isNotEmpty()) {
            Text(
                text = "点击拼音可重选",
                style = TextStyle(
                    color = palette.keyTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * Spacious single-row `34.dp` Candidate Strip inside the constant `42.dp` top chrome slot
 * (paired with [FloatingPreeditCapsule] floating above the keyboard top edge):
 * - 1st candidate riveted at `X = 12.dp` (never pushed right by long pinyin),
 * - Comfortable `29.dp` pill height with generous vertical breathing room,
 * - Followed by scrollable 2..N candidates, right-side `[▾]` expand sheet button and `[×]` clear button.
 */
@Composable
fun CandidateStrip(
    composingCode: String,
    preeditDisplay: String = composingCode,
    inputScheme: InputScheme = InputScheme.FLYPY_TABLE,
    candidates: List<FlypyCandidate>,
    hasMoreCandidates: Boolean = false,
    isSheetExpanded: Boolean,
    onSelectCandidate: (Int) -> Unit,
    onToggleSheet: () -> Unit,
    onClearCode: () -> Unit,
    onRequestMoreCandidates: () -> Unit = {},
    codetableEnhancerActive: Boolean = false,
    pinnedWordsForCurrentCode: Set<String> = emptySet(),
    onPinCandidate: (String) -> Unit = {},
    onUnpinCandidate: (String) -> Unit = {},
    onBlockCandidate: (String) -> Unit = {},
    isOrqDateActive: Boolean = false,
    orqDateOffsetDays: Int = 0,
    onStepOrqDate: ((Int) -> Unit)? = null,
    syllableFilterOptions: List<String> = emptyList(),
    activeSyllable: String = "",
    onSelectSyllable: (String) -> Unit = {}
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val metrics = LocalKeyboardMetrics.current
    val scrollState = rememberScrollState()

    LaunchedEffect(composingCode, preeditDisplay, candidates.firstOrNull()?.text, activeSyllable, syllableFilterOptions) {
        scrollState.scrollTo(0)
    }

    LaunchedEffect(scrollState.value, scrollState.maxValue, hasMoreCandidates) {
        if (hasMoreCandidates && scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 48) {
            onRequestMoreCandidates()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 100% full row dedicated to word/character candidates (Pinyin row sits separately above the divider)
        if (candidates.isEmpty()) {
            val emptyHint = when {
                composingCode.all { it == '`' } -> "反查：输双拼查形(如 ji`) 或 输双形查音(如 `bo)"
                inputScheme.usesRime -> "无匹配候选 · 按回车上屏编码或空格清屏"
                else -> "空码 · 第五码或空格自动清屏"
            }
            Text(
                text = emptyHint,
                style = TextStyle(
                    color = if (composingCode.all { it == '`' }) palette.keyTextMuted else TypeFreeRose,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            // 1. Primary 1st candidate riveted at X = 12.dp (0px horizontal shift regardless of pinyin length)
            val firstCand = candidates.first()
            CandidateChipItem(
                index = 0,
                cand = firstCand,
                isFirst = true,
                codetableEnhancerActive = codetableEnhancerActive,
                isPinned = firstCand.text in pinnedWordsForCurrentCode,
                onClick = { onSelectCandidate(0) },
                onPin = { onPinCandidate(firstCand.text) },
                onUnpin = { onUnpinCandidate(firstCand.text) },
                onBlock = { onBlockCandidate(firstCand.text) }
            )

            // 2. Scrollable 2..N candidates across remaining full width
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.drop(1).take(39).forEachIndexed { offsetIdx, cand ->
                    val actualIdx = offsetIdx + 1
                    CandidateChipItem(
                        index = actualIdx,
                        cand = cand,
                        isFirst = false,
                        codetableEnhancerActive = codetableEnhancerActive,
                        isPinned = cand.text in pinnedWordsForCurrentCode,
                        onClick = { onSelectCandidate(actualIdx) },
                        onPin = { onPinCandidate(cand.text) },
                        onUnpin = { onUnpinCandidate(cand.text) },
                        onBlock = { onBlockCandidate(cand.text) }
                    )
                }
            }
        }

        // 2.5 Optional `orq` Date Offset Stepper Buttons (`[-1天]` `[+1天]`)
        if (isOrqDateActive) {
            val stepActiveBorder = if (orqDateOffsetDays != 0) {
                TypeFreeEmerald.copy(alpha = 0.72f)
            } else {
                palette.keyBorder
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-1 to "-1天", 1 to "+1天").forEach { (delta, label) ->
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.keyCapSecondaryBg)
                            .border(1.dp, stepActiveBorder, RoundedCornerShape(9.dp))
                            .clickable { onStepOrqDate?.invoke(delta) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                color = palette.keyTextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Large Expand All Candidates Grid Button (`[⌄]`)
        if (candidates.size > 2 || hasMoreCandidates || syllableFilterOptions.size > 1) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSheetExpanded) palette.capsuleSelectedBg else palette.keyCapSecondaryBg)
                    .border(
                        width = 1.dp,
                        color = if (isSheetExpanded) TypeFreeEmerald.copy(alpha = 0.72f) else palette.keyBorder,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable(onClick = onToggleSheet),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TypeFreeIcons.ChevronDown,
                    contentDescription = if (isSheetExpanded) "收起候选" else "展开更多候选",
                    tint = palette.keyTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. 1-Tap Clear Code (`[×]`) — omitted in 14-key mode where Row 4 right already provides [清除]
        if (inputScheme != InputScheme.RIME_DOUBLE_PINYIN_FLYPY_14) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.keyCapSecondaryBg)
                    .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                    .clickable(onClick = onClearCode),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TypeFreeIcons.Close,
                    contentDescription = "清空编码",
                    tint = palette.keyTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateChipItem(
    index: Int,
    cand: FlypyCandidate,
    isFirst: Boolean,
    codetableEnhancerActive: Boolean = false,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onPin: () -> Unit = {},
    onUnpin: () -> Unit = {},
    onBlock: () -> Unit = {}
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    var menuExpanded by remember { mutableStateOf(false) }
    val longPressEnabled = codetableEnhancerActive && cand.text.isNotBlank() && !cand.text.startsWith("[")

    Box {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isFirst) palette.capsuleSelectedBg else palette.keyCapBg)
                .border(
                    width = 1.dp,
                    color = if (isFirst) palette.keyTextPrimary.copy(alpha = 0.28f) else palette.keyBorder,
                    shape = RoundedCornerShape(9.dp)
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (longPressEnabled) {
                        { menuExpanded = true }
                    } else {
                        null
                    }
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (index < 3) {
                Text(
                    text = "${index + 1}",
                    style = TextStyle(
                        color = palette.keyTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
            val rawDisplay = cand.displayText.ifEmpty { cand.text }
            val visualCandText = if (rawDisplay.length > 16 || rawDisplay.contains('\n')) {
                rawDisplay.replace('\n', '↵').take(16) + "…"
            } else {
                rawDisplay
            }
            Text(
                text = visualCandText,
                style = TextStyle(
                    color = palette.keyTextPrimary,
                    fontSize = 15.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1
            )
            if (cand.codeHint.isNotEmpty()) {
                Text(
                    text = "〔${cand.codeHint}〕",
                    style = TextStyle(
                        color = palette.keyTextMuted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1
                )
            }
        }

        CandidateActionDropdownMenu(
            expanded = menuExpanded,
            isPinned = isPinned,
            onDismiss = { menuExpanded = false },
            onPin = onPin,
            onUnpin = onUnpin,
            onBlock = onBlock
        )
    }
}

@Composable
private fun CandidateActionDropdownMenu(
    expanded: Boolean,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onBlock: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (isPinned) {
            DropdownMenuItem(
                text = { Text("取消置顶", fontSize = 13.sp) },
                onClick = {
                    onDismiss()
                    onUnpin()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("置顶首选", fontSize = 13.sp) },
                onClick = {
                    onDismiss()
                    onPin()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("屏蔽该词", fontSize = 13.sp, color = TypeFreeRose) },
            onClick = {
                onDismiss()
                onBlock()
            }
        )
    }
}

@Composable
fun FlypyComposingCandidateBar(
    composingCode: String,
    candidates: List<FlypyCandidate>,
    isSheetExpanded: Boolean,
    onSelectCandidate: (Int) -> Unit,
    onToggleSheet: () -> Unit,
    onClearCode: () -> Unit
) {
    CandidateStrip(
        composingCode = composingCode,
        preeditDisplay = composingCode,
        inputScheme = InputScheme.FLYPY_TABLE,
        candidates = candidates,
        isSheetExpanded = isSheetExpanded,
        onSelectCandidate = onSelectCandidate,
        onToggleSheet = onToggleSheet,
        onClearCode = onClearCode
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CandidateGridOverlay(
    candidates: List<FlypyCandidate>,
    hasMoreCandidates: Boolean = false,
    onSelectCandidate: (Int) -> Unit,
    onBackspace: () -> Unit,
    onCollapse: () -> Unit,
    onRequestMoreCandidates: () -> Unit = {},
    codetableEnhancerActive: Boolean = false,
    pinnedWordsForCurrentCode: Set<String> = emptySet(),
    onPinCandidate: (String) -> Unit = {},
    onUnpinCandidate: (String) -> Unit = {},
    onBlockCandidate: (String) -> Unit = {},
    syllableFilterOptions: List<String> = emptyList(),
    lockedSyllables: List<String> = emptyList(),
    activeSyllable: String = "",
    onSelectSyllable: (String) -> Unit = {},
    onUnlockSyllable: (Int) -> Unit = {},
    inputScheme: InputScheme = InputScheme.FLYPY_TABLE,
    onClearCode: (() -> Unit)? = null
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val vScroll = rememberScrollState()

    LaunchedEffect(candidates.firstOrNull()?.text, syllableFilterOptions, lockedSyllables, activeSyllable) {
        vScroll.scrollTo(0)
    }

    LaunchedEffect(hasMoreCandidates, vScroll.value, vScroll.maxValue) {
        if (hasMoreCandidates && (vScroll.maxValue == 0 || vScroll.value >= vScroll.maxValue - 80)) {
            onRequestMoreCandidates()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.keyboardCanvas)
            .border(1.dp, palette.keyBorder, RoundedCornerShape(10.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left vertical Pinyin rail (shows locked syllables at top + next syllable options)
            if (syllableFilterOptions.size > 1 || lockedSyllables.isNotEmpty()) {
                val emeraldText = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.keyCapSecondaryBg.copy(alpha = 0.55f))
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    lockedSyllables.forEachIndexed { idx, lockedSyl ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.capsuleSelectedBg)
                                .border(1.dp, TypeFreeEmerald.copy(alpha = 0.78f), RoundedCornerShape(7.dp))
                                .clickable { onUnlockSyllable(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$lockedSyl ×",
                                color = emeraldText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    syllableFilterOptions.forEachIndexed { idx, syl ->
                        val isLead = lockedSyllables.isEmpty() && idx == 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isLead) palette.capsuleSelectedBg else palette.keyCapBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isLead) TypeFreeEmerald.copy(alpha = 0.55f) else palette.keyBorder,
                                    shape = RoundedCornerShape(7.dp)
                                )
                                .clickable { onSelectSyllable(syl) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = syl,
                                color = if (isLead) emeraldText else palette.keyTextPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = if (isLead) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(palette.keyBorder)
                )
            }

            // Right multi-row word/character candidate grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(vScroll)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidates.forEachIndexed { idx, cand ->
                        var gridMenuExpanded by remember(cand.text, idx) { mutableStateOf(false) }
                        val longPressEnabled = codetableEnhancerActive && cand.text.isNotBlank() && !cand.text.startsWith("[")
                        Box {
                            Row(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (idx == 0) palette.capsuleSelectedBg else palette.keyCapBg)
                                    .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = { onSelectCandidate(idx) },
                                        onLongClick = if (longPressEnabled) {
                                            { gridMenuExpanded = true }
                                        } else {
                                            null
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val rawGridDisplay = cand.displayText.ifEmpty { cand.text }
                                val gridCandText = if (rawGridDisplay.length > 22 || rawGridDisplay.contains('\n')) {
                                    rawGridDisplay.replace('\n', '↵').take(22) + "…"
                                } else {
                                    rawGridDisplay
                                }
                                Text(
                                    text = gridCandText,
                                    color = palette.keyTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium
                                )
                                if (cand.codeHint.isNotEmpty()) {
                                    Text(
                                        text = "〔${cand.codeHint}〕",
                                        color = palette.keyTextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            CandidateActionDropdownMenu(
                                expanded = gridMenuExpanded,
                                isPinned = cand.text in pinnedWordsForCurrentCode,
                                onDismiss = { gridMenuExpanded = false },
                                onPin = { onPinCandidate(cand.text) },
                                onUnpin = { onUnpinCandidate(cand.text) },
                                onBlock = { onBlockCandidate(cand.text) }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.keyCapSecondaryBg)
                    .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⌃ 返回键盘",
                    color = palette.keyTextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.keyCapSecondaryBg)
                    .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TypeFreeIcons.Backspace,
                    contentDescription = "退格",
                    tint = palette.keyTextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            if (onClearCode != null && inputScheme == InputScheme.RIME_DOUBLE_PINYIN_FLYPY_14) {
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.keyCapSecondaryBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            onClearCode()
                            onCollapse()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "清除",
                        color = palette.keyTextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FlypyExpandedCandidateGridOverlay(
    candidates: List<FlypyCandidate>,
    onSelectCandidate: (Int) -> Unit,
    onBackspace: () -> Unit,
    onCollapse: () -> Unit
) {
    CandidateGridOverlay(
        candidates = candidates,
        onSelectCandidate = onSelectCandidate,
        onBackspace = onBackspace,
        onCollapse = onCollapse
    )
}
