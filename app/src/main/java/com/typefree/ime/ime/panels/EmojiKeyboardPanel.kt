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
import androidx.compose.ui.text.style.TextOverflow
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
    val badgeIcon: String,
    val isPlatformShortcode: Boolean = false
) {
    RECENT("常用", "Recent", "🕒"),
    ZHIHU("知乎", "Zhihu", "🐶", isPlatformShortcode = true),
    BAIDU("百度", "Baidu", "😏", isPlatformShortcode = true),
    WECHAT("微信", "WeChat", "🐕", isPlatformShortcode = true),
    SMILEYS("表情", "Smileys", "😀"),
    GESTURES("手势", "People", "👍"),
    SYMBOLS("符号", "Symbols", "❤️"),
    ANIMALS("自然", "Nature", "🐾"),
    FOOD("生活", "Daily", "🍉")
}

data class PlatformSticker(
    val iconHint: String,
    val code: String
)

/**
 * Curated Unicode emoji + Chinese community shortcode sticker catalog
 * (WeChat, Zhihu `[狗头]`, Baidu Tieba `#(滑稽)`, Weibo, Xiaohongshu): 500+ total unique entries across 9 categories.
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

    val zhihuStickers: List<PlatformSticker> = listOf(
        PlatformSticker("🐶", "[狗头]"),
        PlatformSticker("😲", "[惊喜]"),
        PlatformSticker("👍", "[赞同]"),
        PlatformSticker("🤦", "[捂脸]"),
        PlatformSticker("😂", "[飙泪笑]"),
        PlatformSticker("🤔", "[思考]"),
        PlatformSticker("😜", "[调皮]"),
        PlatformSticker("🧐", "[机智]"),
        PlatformSticker("👀", "[好奇]"),
        PlatformSticker("😅", "[尴尬]"),
        PlatformSticker("🍉", "[吃瓜]"),
        PlatformSticker("😄", "[大笑]"),
        PlatformSticker("🤣", "[笑哭]"),
        PlatformSticker("🥺", "[可怜]"),
        PlatformSticker("🙄", "[白眼]"),
        PlatformSticker("😎", "[酷]"),
        PlatformSticker("😳", "[发呆]"),
        PlatformSticker("😒", "[撇嘴]"),
        PlatformSticker("😊", "[害羞]"),
        PlatformSticker("🙏", "[拜托]"),
        PlatformSticker("🤝", "[握手]"),
        PlatformSticker("👏", "[鼓掌]"),
        PlatformSticker("👌", "[赞]"),
        PlatformSticker("🍻", "[干杯]"),
        PlatformSticker("🌹", "[玫瑰]"),
        PlatformSticker("🎈", "[气球]"),
        PlatformSticker("🔥", "[厉害]"),
        PlatformSticker("📣", "[打CALL]"),
        PlatformSticker("😱", "[惊讶]"),
        PlatformSticker("🤭", "[偷笑]"),
        PlatformSticker("😠", "[生气]"),
        PlatformSticker("💔", "[心碎]"),
        PlatformSticker("❤️", "[爱你]"),
        PlatformSticker("🫂", "[抱抱]"),
        PlatformSticker("🙂", "[微笑]"),
        PlatformSticker("😢", "[流泪]")
    )

    val baiduStickers: List<PlatformSticker> = listOf(
        PlatformSticker("😏", "#(滑稽)"),
        PlatformSticker("😈", "#(阴险)"),
        PlatformSticker("😊", "#(笑眼)"),
        PlatformSticker("😜", "#(吐舌)"),
        PlatformSticker("🥺", "#(委屈)"),
        PlatformSticker("😇", "#(乖)"),
        PlatformSticker("😆", "#(太开心)"),
        PlatformSticker("😤", "#(喷)"),
        PlatformSticker("👍", "#(真棒)"),
        PlatformSticker("😒", "#(鄙视)"),
        PlatformSticker("😡", "#(怒)"),
        PlatformSticker("😓", "#(汗)"),
        PlatformSticker("😭", "#(泪)"),
        PlatformSticker("😫", "#(惊哭)"),
        PlatformSticker("😲", "#(惊讶)"),
        PlatformSticker("😎", "#(酷)"),
        PlatformSticker("😰", "#(狂汗)"),
        PlatformSticker("🙁", "#(不高兴)"),
        PlatformSticker("😑", "#(黑线)"),
        PlatformSticker("😅", "#(勉强)"),
        PlatformSticker("😴", "#(睡觉)"),
        PlatformSticker("🤭", "#(捂嘴笑)"),
        PlatformSticker("😉", "#(你懂的)"),
        PlatformSticker("😮", "#(啊)"),
        PlatformSticker("🤑", "#(钱)"),
        PlatformSticker("🌹", "#(玫瑰)"),
        PlatformSticker("🍵", "#(茶杯)"),
        PlatformSticker("🎂", "#(蛋糕)"),
        PlatformSticker("👌", "#(ok)"),
        PlatformSticker("✌️", "#(胜利)"),
        PlatformSticker("❤️", "#(爱心)"),
        PlatformSticker("💔", "#(心碎)"),
        PlatformSticker("😂", "#(哈哈)"),
        PlatformSticker("😍", "#(花心)"),
        PlatformSticker("🤩", "#(酸爽)"),
        PlatformSticker("🤔", "#(疑问)")
    )

    val wechatStickers: List<PlatformSticker> = listOf(
        PlatformSticker("🐕", "[旺柴]"),
        PlatformSticker("🤯", "[裂开]"),
        PlatformSticker("🤦", "[捂脸]"),
        PlatformSticker("🥹", "[破涕为笑]"),
        PlatformSticker("🥲", "[苦涩]"),
        PlatformSticker("😮‍💨", "[叹气]"),
        PlatformSticker("🧐", "[机智]"),
        PlatformSticker("😏", "[奸笑]"),
        PlatformSticker("🤝", "[社会社会]"),
        PlatformSticker("👀", "[让我看看]"),
        PlatformSticker("🍉", "[吃瓜]"),
        PlatformSticker("🔥", "[666]"),
        PlatformSticker("😳", "[发呆]"),
        PlatformSticker("😎", "[得意]"),
        PlatformSticker("🤭", "[偷笑]"),
        PlatformSticker("😈", "[坏笑]"),
        PlatformSticker("🤥", "[抠鼻]"),
        PlatformSticker("👏", "[鼓掌]"),
        PlatformSticker("🙄", "[白眼]"),
        PlatformSticker("😓", "[流汗]"),
        PlatformSticker("😄", "[憨笑]"),
        PlatformSticker("🤔", "[疑问]"),
        PlatformSticker("🔨", "[敲打]"),
        PlatformSticker("👋", "[再见]"),
        PlatformSticker("🙏", "[抱拳]"),
        PlatformSticker("🫂", "[拥抱]"),
        PlatformSticker("👌", "[好的]"),
        PlatformSticker("💪", "[加油]"),
        PlatformSticker("🎆", "[烟花]"),
        PlatformSticker("🧧", "[红包]"),
        PlatformSticker("🤨", "[皱眉]"),
        PlatformSticker("☕", "[悠闲]"),
        PlatformSticker("😵", "[晕]"),
        PlatformSticker("🫠", "[衰]"),
        PlatformSticker("😱", "[惊恐]"),
        PlatformSticker("😊", "[害羞]")
    )

    val smileys: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
        "🤪", "😝", "🤑", "🤗", "🤭", "🫢", "🤫", "🤔",
        "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏",
        "😒", "🙄", "😬", "🫠", "😮‍💨", "😌", "😔", "😪",
        "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧",
        "🥵", "🥶", "🥴", "😵", "😵‍💫", "🤯", "🤠", "🥳",
        "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁",
        "☹️", "😮", "😯", "😲", "😳", "🥺", "🥹", "😦",
        "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖",
        "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡",
        "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡",
        "👹", "👺", "👻", "👽", "👾", "🤖", "😺", "😸",
        "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "🫶"
    )

    val gestures: List<String> = listOf(
        "👍", "👎", "👏", "🙌", "🫶", "👐", "🤲", "🤝",
        "🙏", "✌️", "🤞", "🫰", "🤟", "🤘", "👌", "🤌",
        "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚",
        "🖐️", "🖖", "👋", "🤙", "🫲", "🫱", "💪", "🦾",
        "🦵", "🦶", "✍️", "🫵", "👀", "👁️", "👅", "👄",
        "🫦", "🧠", "🫀", "🫁", "🗣️", "👤", "👥", "🫂",
        "🤦", "🤷", "🙅", "🙆", "🙋", "💁", "🙇", "🫡",
        "💃", "🕺", "🏃", "🚶", "🧘", "🛌", "👨‍💻", "👩‍💻"
    )

    val symbols: List<String> = listOf(
        "❤️", "🩷", "🧡", "💛", "💚", "💙", "🩵", "💜",
        "🖤", "🩶", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️",
        "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
        "🔥", "✨", "💫", "⭐", "🌟", "💥", "💢", "💦",
        "💨", "🕳️", "💬", "💭", "🗯️", "💤", "🎉", "🎊",
        "🎆", "🎇", "🧨", "🧧", "💯", "📌", "📍", "✅",
        "❌", "⭕", "⛔", "🚫", "⚠️", "❓", "❔", "❗",
        "❕", "⚡", "♻️", "🔔", "🔕", "🔇", "🔊", "🔒",
        "🔓", "🔑", "🛡️", "⚖️", "♻️", "➡️", "⬅️", "⬆️"
    )

    val animalsAndNature: List<String> = listOf(
        "🐶", "🐕", "🐩", "🐺", "🐕‍🦺", "🦮", "🐱", "🐈",
        "🐈‍⬛", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
        "🙈", "🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🐣",
        "🦆", "🦅", "🦉", "🦇", "🐗", "🐴", "🦄", "🐝",
        "🦋", "🐌", "🐞", "🐜", "🐢", "🐍", "🦎", "🦖",
        "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠",
        "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐆", "🦓",
        "🦍", "🦧", "🐘", "🦛", "🦏", "🐪", "🦒", "🦘",
        "🌹", "🥀", "🌺", "🌸", "🌼", "🌻", "🌷", "🪷",
        "🍀", "🌿", "🌱", "🌲", "🌳", "🌴", "🌵", "🌾",
        "☀️", "🌤️", "⛅", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️",
        "🌈", "❄️", "☃️", "💨", "🌊", "🌙", "🌛", "🌟"
    )

    val foodAndDaily: List<String> = listOf(
        "🍉", "🍇", "🍓", "🫐", "🍒", "🍑", "🥭", "🍍",
        "🍎", "🍏", "🍐", "🍌", "🍋", "🍊", "🍈", "🥝",
        "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🧄",
        "🍞", "🥐", "🥖", "🥨", "🥯", "🥞", "🧇", "🧀",
        "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🍜",
        "🍝", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙",
        "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁",
        "🍫", "🍬", "🍭", "🍿", "☕", "🧋", "🍵", "🥛",
        "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🥤",
        "🎁", "🎈", "🎀", "🏆", "🥇", "🥈", "🥉", "🎯",
        "🎮", "🕹️", "🎲", "🎵", "🎶", "🎧", "📸", "🎬",
        "💻", "📱", "⌨️", "🖥️", "🚗", "🚕", "✈️", "🚀"
    )

    fun stickersFor(category: EmojiCategory): List<PlatformSticker> = when (category) {
        EmojiCategory.ZHIHU -> zhihuStickers
        EmojiCategory.BAIDU -> baiduStickers
        EmojiCategory.WECHAT -> wechatStickers
        else -> emptyList()
    }

    fun emojisFor(category: EmojiCategory, recentEmojis: List<String> = emptyList()): List<String> {
        return when (category) {
            EmojiCategory.RECENT -> (recentEmojis + defaultHotEmojis).distinct()
            EmojiCategory.ZHIHU -> zhihuStickers.map { it.code }
            EmojiCategory.BAIDU -> baiduStickers.map { it.code }
            EmojiCategory.WECHAT -> wechatStickers.map { it.code }
            EmojiCategory.SMILEYS -> smileys
            EmojiCategory.GESTURES -> gestures
            EmojiCategory.SYMBOLS -> symbols
            EmojiCategory.ANIMALS -> animalsAndNature
            EmojiCategory.FOOD -> foodAndDaily
        }
    }

    fun isShortcode(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length >= 3) ||
            (trimmed.startsWith("#(") && trimmed.endsWith(")") && trimmed.length >= 4)
    }

    fun totalUniqueCount(): Int =
        (defaultHotEmojis +
            zhihuStickers.map { it.code } +
            baiduStickers.map { it.code } +
            wechatStickers.map { it.code } +
            smileys + gestures + symbols + animalsAndNature + foodAndDaily)
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
                val platformHint = when (activeCategory) {
                    EmojiCategory.ZHIHU -> tr("发送后显示知乎原图", "Renders on Zhihu", "發送後顯示知乎原圖")
                    EmojiCategory.BAIDU -> tr("发送后显示贴吧原图", "Renders on Baidu Tieba", "發送後顯示貼吧原圖")
                    EmojiCategory.WECHAT -> tr("发送后显示微信原图", "Renders on WeChat", "發送後顯示微信原圖")
                    else -> ""
                }
                if (platformHint.isNotEmpty()) {
                    Text(
                        text = platformHint,
                        color = palette.keyTextMuted.copy(alpha = 0.78f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

        // 2. Emoji / Platform Sticker Grid
        if (activeCategory.isPlatformShortcode) {
            val stickers = remember(activeCategory) { EmojiCatalog.stickersFor(activeCategory) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(stickers, key = { "${activeCategory.name}_${it.code}" }) { sticker ->
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.keyCapBg)
                            .border(1.dp, palette.keyBorder, RoundedCornerShape(9.dp))
                            .clickable { onSelectEmoji(sticker.code) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = sticker.iconHint,
                            fontSize = 16.sp
                        )
                        Text(
                            text = sticker.code,
                            color = palette.keyTextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
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
                    val isCode = EmojiCatalog.isShortcode(emoji)
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isCode) {
                                    Modifier
                                        .background(palette.keyCapBg)
                                        .border(1.dp, palette.keyBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 2.dp)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelectEmoji(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCode) {
                            Text(
                                text = emoji,
                                color = palette.keyTextPrimary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = emoji,
                                fontSize = 23.sp
                            )
                        }
                    }
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
