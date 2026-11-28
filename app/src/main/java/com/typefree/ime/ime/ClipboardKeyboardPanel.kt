package com.typefree.ime.ime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typefree.ime.data.ClipboardEntry
import com.typefree.ime.ime.clipboard.ClipboardBigBangTokenizer
import com.typefree.ime.ime.clipboard.ClipboardFilterTab
import com.typefree.ime.ime.clipboard.ClipboardSmartExtractor
import com.typefree.ime.ui.components.TypeFreeIcons
import com.typefree.ime.ui.theme.LocalTypeFreeKeyboardPalette
import com.typefree.ime.ui.theme.TypeFreeAmber
import com.typefree.ime.ui.theme.TypeFreeEmerald
import com.typefree.ime.ui.theme.TypeFreeRose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Built-in IME Clipboard & Pinned Snippets Manager (`v3.0.0 Smart Clipboard 2.0`).
 *
 * Features:
 * 1. Multi-Entity Extraction Chips (`ClipboardSmartExtractor.extractAll`): Cainiao/Fengchao pickup codes
 *    (`3-2-1048`, `425666`), tracking numbers (`SF...`, `JD...`), OTP codes, URLs, phones, emails, and addresses.
 * 2. Local "BigBang (💥 拆词)" Word Splitter (`ClipboardBigBangTokenizer`): splits any clipboard card into
 *    clickable word/entity chips for fine-grained multi-word assembly and one-tap paste.
 */
@Composable
fun ClipboardKeyboardPanel(
    items: List<ClipboardEntry>,
    clipboardEnabled: Boolean,
    selectedEditorText: String,
    onPasteText: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearUnpinned: () -> Unit,
    onSaveSelectionToClipboard: () -> Unit,
    onEnableClipboard: () -> Unit,
    onOpenClipboardSettings: () -> Unit,
    onClose: () -> Unit,
    onHideKeyboard: () -> Unit,
    onInsertSpace: () -> Unit,
    onBackspace: () -> Unit,
    pinnedItems: List<ClipboardEntry> = items.filter { it.isPinned },
    smartItems: List<ClipboardEntry> = items.filter { ClipboardSmartExtractor.extractPrimary(it.text) != null },
    currentEditorText: String = "",
    onCopyToPinned: (String) -> Unit = onTogglePin,
    onDeleteInTab: (ClipboardFilterTab, String) -> Unit = { _, id -> onDeleteItem(id) },
    onClearTab: (ClipboardFilterTab) -> Unit = { _ -> onClearUnpinned() },
    onSaveTextToTab: (ClipboardFilterTab) -> Unit = { _ -> onSaveSelectionToClipboard() }
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    var activeTab by remember { mutableStateOf(ClipboardFilterTab.ALL) }
    var confirmClearArmed by remember { mutableStateOf(false) }
    var bigBangEntry by remember { mutableStateOf<ClipboardEntry?>(null) }

    val pinnedCount = pinnedItems.size
    val smartCount = smartItems.size
    val pinnedTextSet = remember(pinnedItems) { pinnedItems.map { it.text }.toSet() }
    val filteredItems = when (activeTab) {
        ClipboardFilterTab.ALL -> items
        ClipboardFilterTab.PINNED -> pinnedItems
        ClipboardFilterTab.SMART -> smartItems
    }
    val savableEditorText = remember(selectedEditorText, currentEditorText) {
        selectedEditorText.trim().ifBlank { currentEditorText.trim() }
    }

    LaunchedEffect(confirmClearArmed) {
        if (confirmClearArmed) {
            delay(3000L)
            confirmClearArmed = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Top 34.dp Header: Filter Tabs (or BigBang Header) + Actions + Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val activeBigBang = bigBangEntry
            if (activeBigBang != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.keyCapBg)
                            .border(1.dp, palette.keyBorder, RoundedCornerShape(14.dp))
                            .clickable { bigBangEntry = null }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "← 列表",
                            color = palette.keyTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = TypeFreeIcons.SplitWord,
                        contentDescription = null,
                        tint = TypeFreeEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "大爆炸拆词 · 点选词块自由组合粘贴",
                        color = palette.keyTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Segmented Filter Track (`全部` | `常用` | `提取`)
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.capsuleTrackBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(16.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ClipboardFilterPill(
                        label = if (items.isNotEmpty()) "全部 ${items.size}" else "全部",
                        selected = activeTab == ClipboardFilterTab.ALL,
                        onClick = { activeTab = ClipboardFilterTab.ALL }
                    )
                    ClipboardFilterPill(
                        label = if (pinnedCount > 0) "常用 $pinnedCount" else "常用",
                        leadingIcon = TypeFreeIcons.Pin,
                        selected = activeTab == ClipboardFilterTab.PINNED,
                        onClick = { activeTab = ClipboardFilterTab.PINNED }
                    )
                    ClipboardFilterPill(
                        label = if (smartCount > 0) "提取 $smartCount" else "提取",
                        leadingIcon = TypeFreeIcons.CopilotStar,
                        selected = activeTab == ClipboardFilterTab.SMART,
                        onClick = { activeTab = ClipboardFilterTab.SMART }
                    )
                }
            }

            // Right Action Cluster
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!clipboardEnabled) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TypeFreeEmerald.copy(alpha = 0.16f))
                            .border(1.dp, TypeFreeEmerald.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable(onClick = onEnableClipboard)
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "开启记录",
                            color = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else if (bigBangEntry == null && (activeTab == ClipboardFilterTab.PINNED || activeTab == ClipboardFilterTab.SMART)) {
                    val canQuickSave = savableEditorText.isNotBlank()
                    val quickLabel = if (canQuickSave) "+ 存当前" else "+ 添加"
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TypeFreeEmerald.copy(alpha = 0.15f))
                            .border(1.dp, TypeFreeEmerald.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (canQuickSave) onSaveTextToTab(activeTab) else onOpenClipboardSettings()
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = quickLabel,
                            color = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                } else if (selectedEditorText.isNotBlank() && bigBangEntry == null) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TypeFreeEmerald.copy(alpha = 0.15f))
                            .border(1.dp, TypeFreeEmerald.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
                            .clickable(onClick = onSaveSelectionToClipboard)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ 存选区",
                            color = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                if (clipboardEnabled && filteredItems.isNotEmpty() && bigBangEntry == null) {
                    val clearBg by animateColorAsState(
                        targetValue = if (confirmClearArmed) {
                            TypeFreeRose.copy(alpha = 0.16f)
                        } else {
                            palette.keyCapBg
                        },
                        animationSpec = tween(140),
                        label = "clearBg"
                    )
                    val clearBorder = if (confirmClearArmed) {
                        TypeFreeRose.copy(alpha = 0.6f)
                    } else {
                        palette.keyBorder
                    }
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(clearBg)
                            .border(1.dp, clearBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                if (confirmClearArmed) {
                                    confirmClearArmed = false
                                    onClearTab(activeTab)
                                } else {
                                    confirmClearArmed = true
                                }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = TypeFreeIcons.Trash,
                            contentDescription = "清空当前分页",
                            tint = if (confirmClearArmed) TypeFreeRose else palette.keyTextMuted,
                            modifier = Modifier.size(12.5.dp)
                        )
                        if (confirmClearArmed) {
                            Text(
                                text = "确认?",
                                color = TypeFreeRose,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Manage Snippets / Settings Icon Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, CircleShape)
                        .clickable(onClick = onOpenClipboardSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.Tune,
                        contentDescription = "剪贴板与常用语设置",
                        tint = palette.keyTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Close Clipboard Panel Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.capsuleSelectedBg)
                        .border(1.dp, palette.keyBorder, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.Close,
                        contentDescription = "返回键盘",
                        tint = palette.keyTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 2. Center Body: BigBang Word Splitter OR 2-Column Bento Grid OR Empty/Disabled State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val activeBigBang = bigBangEntry
            when {
                activeBigBang != null -> {
                    ClipboardBigBangSubPanel(
                        entry = activeBigBang,
                        onPasteSelected = { selectedText ->
                            onPasteText(selectedText)
                            bigBangEntry = null
                        },
                        onBack = { bigBangEntry = null }
                    )
                }

                !clipboardEnabled -> {
                    ClipboardEmptyOrDisabledCard(
                        title = "剪贴板历史记录已暂停",
                        subtitle = "开启后自动收纳复制的文本、短信验证码与链接，100% 仅保存在本机沙盒",
                        primaryActionLabel = "开启剪贴板记录",
                        onPrimaryAction = onEnableClipboard
                    )
                }

                filteredItems.isEmpty() -> {
                    val (emptyTitle, emptySub) = when (activeTab) {
                        ClipboardFilterTab.ALL -> {
                            "剪贴板暂无记录" to "复制任意文字、验证码或网址会自动出现在这里（删后重新复制可再次收录）"
                        }
                        ClipboardFilterTab.PINNED -> {
                            "暂无常用短语" to "在「全部」点 📌 正向存入，或在此一键保存当前文字 / 前往设置页手动输入"
                        }
                        ClipboardFilterTab.SMART -> {
                            "暂无智能提取内容" to "复制验证码/取件码/单号/链接/手机号会自动正向同步至此，也可手动添加"
                        }
                    }
                    val hasSavableText = savableEditorText.isNotBlank()
                    val emptyActionLabel = when (activeTab) {
                        ClipboardFilterTab.ALL -> if (hasSavableText) "📌 将当前文字存为常用" else "+ 手动添加常用短语"
                        ClipboardFilterTab.PINNED -> if (hasSavableText) "📌 存当前文字为常用" else "+ 手动输入常用短语"
                        ClipboardFilterTab.SMART -> if (hasSavableText) "✨ 存当前文字到提取" else "+ 手动添加提取内容"
                    }
                    ClipboardEmptyOrDisabledCard(
                        title = emptyTitle,
                        subtitle = emptySub,
                        primaryActionLabel = emptyActionLabel,
                        onPrimaryAction = {
                            if (hasSavableText) {
                                onSaveTextToTab(activeTab)
                            } else {
                                onOpenClipboardSettings()
                            }
                        }
                    )
                }

                else -> {
                    val nowMs = remember(filteredItems) { System.currentTimeMillis() }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(
                            items = filteredItems,
                            key = { "${activeTab.name}_${it.id}" }
                        ) { entry ->
                            ClipboardItemCard(
                                entry = entry,
                                nowMs = nowMs,
                                showPinAction = true,
                                isAlreadyInPinned = activeTab == ClipboardFilterTab.PINNED || entry.text in pinnedTextSet,
                                onPasteFull = { onPasteText(entry.text) },
                                onPasteExtracted = { token -> onPasteText(token) },
                                onOpenBigBang = { bigBangEntry = entry },
                                onTogglePin = { onCopyToPinned(entry.id) },
                                onDelete = { onDeleteInTab(activeTab, entry.id) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom Utility Bar (28.dp): Hide Keyboard | Back to Keyboard + Status | Space | Repeating Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.keyCapSecondaryBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                        .clickable(onClick = onHideKeyboard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.ChevronDown,
                        contentDescription = "收起键盘",
                        tint = palette.keyTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "← 返回键盘",
                        color = palette.keyTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Text(
                    text = if (bigBangEntry != null) "点选词块组合 · 点「粘贴已选」上屏" else "点卡片粘贴 · 拆词 · 置顶",
                    color = palette.keyTextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                        .clickable(onClick = onInsertSpace),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "空格",
                        color = palette.keyTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                ClipboardRepeatingBackspaceKey(
                    onBackspace = onBackspace
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipboardBigBangSubPanel(
    entry: ClipboardEntry,
    onPasteSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val tokens = remember(entry.text) {
        ClipboardBigBangTokenizer.tokenize(entry.text)
    }
    var selectedIndices by remember(entry.id) {
        // Pre-select entity tokens if any, otherwise empty so user can tap freely
        val entityIdx = tokens.filter { it.isEntity }.map { it.index }.toSet()
        mutableStateOf(entityIdx)
    }
    val assembledPreview = remember(tokens, selectedIndices) {
        ClipboardBigBangTokenizer.joinSelectedTokens(tokens, selectedIndices)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.keyCapBg)
            .border(1.dp, palette.keyBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Scrollable FlowRow of clickable word & entity tokens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tokens.forEach { token ->
                    val isSelected = selectedIndices.contains(token.index)
                    val chipBg = when {
                        isSelected -> TypeFreeEmerald
                        token.isEntity -> TypeFreeEmerald.copy(alpha = 0.12f)
                        token.isPunctuation -> palette.keyboardCanvas.copy(alpha = 0.6f)
                        else -> palette.cardElevatedBg
                    }
                    val chipBorder = when {
                        isSelected -> TypeFreeEmerald
                        token.isEntity -> TypeFreeEmerald.copy(alpha = 0.45f)
                        else -> palette.keyBorder
                    }
                    val chipTextColor = when {
                        isSelected -> Color.White
                        token.isEntity -> if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                        token.isPunctuation -> palette.keyTextMuted
                        else -> palette.keyTextPrimary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedIndices = if (isSelected) {
                                    selectedIndices - token.index
                                } else {
                                    selectedIndices + token.index
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = token.text,
                            color = chipTextColor,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected || token.isEntity) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Bottom Action Strip: Quick Select Controls + Paste Selected
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.cardElevatedBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            selectedIndices = tokens.map { it.index }.toSet()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "全选",
                        color = palette.keyTextPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.cardElevatedBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            selectedIndices = tokens.filter { !it.isPunctuation }.map { it.index }.toSet()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "去标点",
                        color = palette.keyTextPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.cardElevatedBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                        .clickable { selectedIndices = emptySet() }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "清空",
                        color = palette.keyTextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (assembledPreview.isNotBlank()) TypeFreeEmerald else palette.cardElevatedBg
                        )
                        .clickable(enabled = assembledPreview.isNotBlank()) {
                            onPasteSelected(assembledPreview)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (assembledPreview.isNotBlank()) {
                            "✓ 粘贴已选 (${selectedIndices.size})"
                        } else {
                            "请点选词块"
                        },
                        color = if (assembledPreview.isNotBlank()) Color.White else palette.keyTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardFilterPill(
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) palette.capsuleSelectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) palette.keyTextPrimary else palette.keyTextMuted,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = label,
            color = if (selected) palette.keyTextPrimary else palette.keyTextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ClipboardItemCard(
    entry: ClipboardEntry,
    nowMs: Long,
    showPinAction: Boolean = true,
    isAlreadyInPinned: Boolean = entry.isPinned,
    onPasteFull: () -> Unit,
    onPasteExtracted: (String) -> Unit,
    onOpenBigBang: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = tween(90),
        label = "clipCardScale"
    )

    val allExtractions = remember(entry.text) {
        ClipboardSmartExtractor.extractAll(entry.text)
    }
    val smartChips = remember(entry.text, allExtractions) {
        allExtractions.filter { it.extractedValue != entry.text.trim() }
    }
    val primarySmart = allExtractions.firstOrNull()
    val showSeparateSmartChip = smartChips.isNotEmpty()
    val displayTime = remember(entry.timestamp, nowMs) {
        ClipboardSmartExtractor.formatRelativeTime(entry.timestamp, nowMs)
    }
    val normalizedBody = remember(entry.text) {
        entry.text
            .trim()
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    val cardBorderColor = when {
        isAlreadyInPinned -> TypeFreeAmber.copy(alpha = if (palette.isDark) 0.55f else 0.65f)
        else -> palette.keyBorder
    }
    val cardBgColor = when {
        isAlreadyInPinned && palette.isDark -> Color(0xFF282621)
        isAlreadyInPinned && !palette.isDark -> Color(0xFFFFFCF5)
        else -> palette.keyCapBg
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBgColor)
            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPasteFull
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Card Meta & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isAlreadyInPinned) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(TypeFreeAmber.copy(alpha = 0.18f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (showPinAction) "已常用" else "常用",
                            color = if (palette.isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else if (primarySmart != null && !showSeparateSmartChip) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(TypeFreeEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = primarySmart.kind.badgePrefix,
                            color = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = displayTime,
                    color = palette.keyTextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // BigBang Word Splitter Trigger
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.cardElevatedBg)
                        .clickable(onClick = onOpenBigBang)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.SplitWord,
                        contentDescription = "拆词",
                        tint = palette.keyTextMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "拆词",
                        color = palette.keyTextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                if (showPinAction) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAlreadyInPinned) TypeFreeAmber.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable(onClick = onTogglePin),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TypeFreeIcons.Pin,
                            contentDescription = if (isAlreadyInPinned) "取消常用" else "存入常用",
                            tint = if (isAlreadyInPinned) TypeFreeAmber else palette.keyTextMuted,
                            modifier = Modifier.size(12.5.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.Close,
                        contentDescription = "删除此条",
                        tint = palette.keyTextMuted.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Card Text Preview
        Text(
            text = normalizedBody,
            color = palette.keyTextPrimary,
            fontSize = 12.5.sp,
            lineHeight = 16.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = if (showSeparateSmartChip) 2 else 3,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        // Multi-Entity Smart Extraction Chips (Pickup Code / Tracking / OTP / URL / Phone / Address)
        if (showSeparateSmartChip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                smartChips.forEach { smart ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TypeFreeEmerald.copy(alpha = 0.14f))
                            .border(1.dp, TypeFreeEmerald.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable { onPasteExtracted(smart.extractedValue) }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = smart.chipLabel,
                            color = if (palette.isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardEmptyOrDisabledCard(
    title: String,
    subtitle: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.keyCapBg)
            .border(1.dp, palette.keyBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = TypeFreeIcons.Clipboard,
            contentDescription = null,
            tint = palette.keyTextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = palette.keyTextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            color = palette.keyTextMuted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(palette.primaryMicSolid)
                .clickable(onClick = onPrimaryAction)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = primaryActionLabel,
                color = palette.onPrimaryMic,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ClipboardRepeatingBackspaceKey(
    onBackspace: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val currentOnBackspace by rememberUpdatedState(onBackspace)
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(if (pressed) palette.capsuleSelectedBg else palette.keyCapSecondaryBg)
            .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        currentOnBackspace()
                        val repeatJob = launch {
                            delay(380L)
                            while (isActive) {
                                currentOnBackspace()
                                delay(55L)
                            }
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                            }
                        } finally {
                            repeatJob.cancel()
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = TypeFreeIcons.Backspace,
            contentDescription = "退格删除",
            tint = palette.keyTextPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}
