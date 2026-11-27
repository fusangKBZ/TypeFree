package com.typefree.ime.ime.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typefree.ime.i18n.tr
import com.typefree.ime.ui.components.TypeFreeIcons
import com.typefree.ime.ui.theme.LocalTypeFreeKeyboardPalette
import com.typefree.ime.ui.theme.TypeFreeEmerald
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class EmojiCategory(
    val labelZh: String,
    val labelEn: String,
    val badgeIcon: String
) {
    RECENT("常用", "Recent", "🕒"),
    SMILEYS("表情", "Smileys", "😀"),
    GESTURES("手势", "People", "👍"),
    SYMBOLS("符号", "Symbols", "❤️"),
    ANIMALS("自然", "Nature", "🐶"),
    FOOD("生活", "Daily", "🍉")
}

/**
 * Curated mainstream Unicode emoji catalog tailored for Chinese social/community apps
 * (WeChat, Zhihu, Weibo, Xiaohongshu, Bilibili, Feishu): ~240 high-frequency emojis across 6 categories.
 */
object EmojiCatalog {

    val defaultHotEmojis: List<String> = listOf(
        "😂", "🤣", "😭", "😅", "🥹", "😊", "😍", "🥰",
        "😘", "🤔", "😏", "🙄", "😒", "😤", "😡", "🤯",
        "😱", "😳", "🥳", "🥺", "🙏", "👍", "👎", "👏",
        "🤝", "👌", "✌️", "🫶", "❤️", "💔", "🔥", "✨",
        "🎉", "💯", "👀", "💀", "🤡", "🐶", "🐱", "🌹",
        "🍉", "☕", "🍺", "🚀", "💡", "✅", "❌", "⚡"
    )

    val smileys: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
        "🤪", "😝", "🤑", "🤗", "🤭", "🫢", "🤫", "🤔",
        "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏",
        "😒", "🙄", "😬", "🫠", "😮‍💨", "😌", "😔", "😪",
        "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧",
        "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸",
        "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮",
        "😯", "😲", "😳", "🥺", "🥹", "😦", "😧", "😨",
        "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞",
        "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬",
        "😈", "💀", "☠️", "💩", "🤡", "👻", "👽", "🤖"
    )

    val gestures: List<String> = listOf(
        "👍", "👎", "👏", "🙌", "🫶", "👐", "🤲", "🤝",
        "🙏", "✌️", "🤞", "🫰", "🤟", "🤘", "👌", "🤌",
        "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚",
        "🖐️", "🖖", "👋", "🤙", "💪", "🦵", "🦶", "✍️",
        "🫵", "👀", "👁️", "🧠", "🫀", "🗣️", "🤦", "🤷",
        "🙅", "🙆", "🙋", "🙇", "🫡", "💃", "🕺", "🫂"
    )

    val symbols: List<String> = listOf(
        "❤️", "🩷", "🧡", "💛", "💚", "💙", "💜", "🖤",
        "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞",
        "💓", "💗", "💖", "💘", "💝", "💟", "🔥", "✨",
        "💫", "⭐", "🌟", "💥", "💢", "💦", "💨", "🕳️",
        "💬", "💭", "🗯️", "💤", "🎉", "🎊", "💯", "📌",
        "✅", "❌", "⚠️", "❓", "❗", "⚡", "♻️", "🔔"
    )

    val animalsAndNature: List<String> = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
        "🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅",
        "🦉", "🐴", "🦄", "🐝", "🦋", "🐌", "🐞", "🐢",
        "🐍", "🦖", "🐳", "🐬", "🐟", "🦈", "🌹", "🥀",
        "🌺", "🌸", "🌼", "🌻", "🌷", "🍀", "🌿", "🌵",
        "☀️", "🌤️", "⛅", "🌧️", "⛈️", "🌈", "❄️", "🌙"
    )

    val foodAndDaily: List<String> = listOf(
        "🍉", "🍇", "🍓", "🍒", "🍑", "🥭", "🍍", "🍎",
        "🍌", "🍋", "🍊", "🥑", "🥦", "🌽", "🍞", "🥐",
        "🍔", "🍟", "🍕", "🌮", "🍜", "🍲", "🍣", "🥟",
        "🍦", "🍰", "🎂", "🍫", "🍿", "☕", "🧋", "🍵",
        "🍺", "🍻", "🥂", "🍷", "🥤", "🎁", "🎈", "🏆",
        "🥇", "🎯", "🎮", "🎵", "📸", "💻", "📱", "🚀"
    )

    fun emojisFor(category: EmojiCategory, recentEmojis: List<String> = emptyList()): List<String> {
        return when (category) {
            EmojiCategory.RECENT -> (recentEmojis + defaultHotEmojis).distinct()
            EmojiCategory.SMILEYS -> smileys
            EmojiCategory.GESTURES -> gestures
            EmojiCategory.SYMBOLS -> symbols
            EmojiCategory.ANIMALS -> animalsAndNature
            EmojiCategory.FOOD -> foodAndDaily
        }
    }

    fun totalUniqueCount(): Int =
        (defaultHotEmojis + smileys + gestures + symbols + animalsAndNature + foodAndDaily)
            .toSet()
            .size
}

/**
 * Built-in mainstream Emoji picker overlay replacing the main keyboard area while keeping
 * `metrics.frameHeight` exact (`0px` host viewport jump).
 */
@Composable
fun EmojiKeyboardPanel(
    recentEmojis: List<String>,
    onSelectEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    var activeCategory by remember { mutableStateOf(EmojiCategory.RECENT) }
    val emojis = remember(activeCategory, recentEmojis) {
        EmojiCatalog.emojisFor(activeCategory, recentEmojis)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Compact Header Row (matches TypeFreeTopBar height / style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tr("表情 Emoji", "Emoji", "表情 Emoji"),
                    color = palette.keyTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${activeCategory.labelZh} · ${emojis.size}",
                    color = palette.keyTextMuted,
                    fontSize = 11.5.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("返回键盘", "Keyboard", "返回鍵盤"),
                        color = palette.keyTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.keyCapBg)
                        .border(1.dp, palette.keyBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onHideKeyboard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TypeFreeIcons.KeyboardHide,
                        contentDescription = tr("收起键盘", "Hide Keyboard", "收起鍵盤"),
                        tint = palette.keyTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. 8-column Emoji Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojis, key = { "${activeCategory.name}_$it" }) { emoji ->
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectEmoji(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 23.sp
                    )
                }
            }
        }

        // 3. Bottom Category Tabs + Backspace Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(palette.keyCapSecondaryBg)
                    .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    color = palette.keyTextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                EmojiCategory.entries.forEach { cat ->
                    val selected = cat == activeCategory
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) palette.capsuleSelectedBg else palette.keyCapBg)
                            .border(
                                width = 1.dp,
                                color = if (selected) TypeFreeEmerald.copy(alpha = 0.72f) else palette.keyBorder,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable { activeCategory = cat }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.labelZh,
                            color = if (selected) TypeFreeEmerald else palette.keyTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            EmojiRepeatingBackspaceKey(
                onBackspace = onBackspace,
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun EmojiRepeatingBackspaceKey(
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTypeFreeKeyboardPalette.current
    val currentOnBackspace by rememberUpdatedState(onBackspace)
    var isHolding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isHolding) palette.keyBorder else palette.keyCapSecondaryBg)
            .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
            .pointerInput(Unit) {
                coroutineScope {
                    val scope = this
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isHolding = true
                        var repeating = false
                        val job = scope.launch {
                            delay(320L)
                            repeating = true
                            while (isActive) {
                                currentOnBackspace()
                                delay(48L)
                            }
                        }
                        var released = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    released = true
                                    break
                                }
                            }
                        } finally {
                            isHolding = false
                            job.cancel()
                        }
                        if (released && !repeating) {
                            currentOnBackspace()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = TypeFreeIcons.Backspace,
            contentDescription = tr("退格删除", "Backspace", "退格刪除"),
            tint = palette.keyTextPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}
