package com.typefree.ime.ime.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typefree.ime.i18n.tr
import com.typefree.ime.ime.AskAiTurn
import com.typefree.ime.ime.ImeUiAction
import com.typefree.ime.ime.ImeUiState
import com.typefree.ime.ime.LocalKeyboardMetrics
import com.typefree.ime.ui.components.TypeFreeIcons
import com.typefree.ime.ui.theme.LocalTypeFreeKeyboardPalette
import com.typefree.ime.ui.theme.TypeFreeEmerald

private val AskAiIndigo = Color(0xFF6366F1)
private val AskAiSky = Color(0xFF0EA5E9)

/**
 * In-keyboard AI preview card panel (v3.5.0):
 * Displays fast, safe AI answers inside the keyboard viewport without modifying host input field.
 */
@Composable
fun AskAiCardPanel(
    uiState: ImeUiState,
    onAction: (ImeUiAction) -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val s = LocalKeyboardMetrics.current.elementScale
    val query = uiState.askAiQuery
    val answer = uiState.askAiAnswer
    val isLoading = uiState.isAskAiLoading
    val turns = uiState.askAiHistory.ifEmpty {
        if (query.isNotBlank() || answer.isNotBlank() || uiState.askAiErrorMessage != null) {
            listOf(
                AskAiTurn(
                    query = query,
                    answer = answer,
                    isLoading = isLoading,
                    errorMessage = uiState.askAiErrorMessage
                )
            )
        } else {
            emptyList()
        }
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(turns.size, answer, isLoading) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = (8f * s).dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. Header (30.dp): Left turn status + Right [新对话] [返回键盘] [✕]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AskAiIndigo.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔍",
                        fontSize = 10.sp
                    )
                }
                val headerTitle = if (turns.size > 1) {
                    tr("连续问答 · 第${turns.size}轮", "Follow-up · Turn ${turns.size}", "連續問答 · 第${turns.size}輪")
                } else {
                    "Q: $query"
                }
                Text(
                    text = headerTitle,
                    color = palette.keyTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (turns.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.keyCapBg)
                            .border(1.dp, palette.keyBorder, RoundedCornerShape(12.dp))
                            .clickable { onAction(ImeUiAction.ResetAskAiConversation) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("新对话", "New", "新對話"),
                            color = palette.keyTextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // [↩ 返回键盘]
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(12.dp))
                        .clickable { onAction(ImeUiAction.ReturnFromAskAiToQwerty) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.KeyboardGrid,
                        contentDescription = tr("返回键盘", "Back to Keyboard", "返回鍵盤"),
                        tint = palette.keyTextPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = tr("返回键盘", "Keyboard", "返回鍵盤"),
                        color = palette.keyTextPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // [✕]
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, CircleShape)
                        .clickable { onAction(ImeUiAction.ReturnFromAskAiToQwerty) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.Close,
                        contentDescription = tr("关闭", "Close", "關閉"),
                        tint = palette.keyTextMuted,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // 2. Middle Card: Scrollable multi-turn Q&A history (weight 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.cardElevatedBg)
                .border(1.dp, palette.keyBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (turns.isEmpty()) {
                Text(
                    text = tr("（无回答内容）", "(No answer)", "（無回答內容）"),
                    color = palette.keyTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    turns.forEachIndexed { idx, turn ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AskAiIndigo.copy(alpha = if (palette.isDark) 0.18f else 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (turns.size > 1) "Q${idx + 1}: ${turn.query}" else "Q: ${turn.query}",
                                    color = if (palette.isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp
                                )
                            }

                            when {
                                turn.isLoading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = AskAiIndigo
                                        )
                                        Text(
                                            text = tr("🔍 AI 正在检索解答…", "🔍 AI is thinking…", "🔍 AI 正在檢索解答…"),
                                            color = palette.keyTextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                turn.errorMessage != null -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = turn.errorMessage,
                                            color = Color(0xFFEF4444),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(palette.keyCapBg)
                                                .border(1.dp, palette.keyBorder, RoundedCornerShape(10.dp))
                                                .clickable { onAction(ImeUiAction.RetryAskAiCard) }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = tr("重试", "Retry", "重試"),
                                                color = palette.keyTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    Text(
                                        text = turn.answer.ifBlank { tr("（无回答内容）", "(No answer)", "（無回答內容）") },
                                        color = palette.keyTextPrimary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Follow-up Chips + Bottom Action Row
        if (!isLoading && answer.isNotBlank()) {
            val quickFollowUps = listOf(
                tr("展开细说", "More details", "展開細說"),
                tr("举个例子", "Give an example", "舉個例子"),
                tr("再简短点", "Make it shorter", "再簡短點"),
                tr("换种方式解释", "Explain differently", "換種方式解釋")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickFollowUps.forEach { chip ->
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AskAiIndigo.copy(alpha = if (palette.isDark) 0.16f else 0.08f))
                            .border(1.dp, AskAiIndigo.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable { onAction(ImeUiAction.SendAskAiQuickFollowUp(chip)) }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↳ $chip",
                            color = if (palette.isDark) Color(0xFFA5B4FC) else Color(0xFF4F46E5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Button 1: ✓ 清空
                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(10.dp))
                        .clickable { onAction(ImeUiAction.DismissAskAiAndClearEditor) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("✓ 清空", "✓ Clear", "✓ 清空"),
                        color = palette.keyTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                // Button 2: 📋 复制
                Box(
                    modifier = Modifier
                        .weight(0.75f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(10.dp))
                        .clickable { onAction(ImeUiAction.CopyAskAiAnswer) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("📋 复制", "📋 Copy", "📋 複製"),
                        color = palette.keyTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                // Button 3: 💬 追问 (keep context & return to keyboard to type follow-up)
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AskAiIndigo.copy(alpha = if (palette.isDark) 0.22f else 0.12f))
                        .border(1.dp, AskAiIndigo.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .clickable { onAction(ImeUiAction.StartAskAiFollowUp) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("💬 追问", "💬 Ask more", "💬 追問"),
                        color = if (palette.isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Button 4: 填入 ↵
                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TypeFreeEmerald)
                        .clickable { onAction(ImeUiAction.CommitAskAiAnswerToEditor) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("填入 ↵", "Insert ↵", "填入 ↵"),
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
