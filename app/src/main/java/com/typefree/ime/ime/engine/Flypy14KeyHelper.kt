package com.typefree.ime.ime.engine

/**
 * Key definition for the 14-key QWERTY-merged Xiaohe Shuangpin layout:
 * Row 1: QW  ER  TY  UI  OP
 * Row 2: AS  DF  GH  JK  L
 * Row 3: ZX  CV  BN  M   (Backspace)
 */
data class Flypy14KeySpec(
    val leftChar: Char,
    val leftHint: String,
    val rightChar: Char? = null,
    val rightHint: String = "",
    val tapToken: Char
) {
    val isDual: Boolean get() = rightChar != null
    val primaryLabel: String
        get() = if (rightChar != null) {
            "${leftChar.uppercaseChar()}${rightChar.uppercaseChar()}"
        } else {
            leftChar.uppercaseChar().toString()
        }
}

/**
 * Pure Kotlin helper for 14-key Xiaohe Double Pinyin (`double_pinyin_flypy_14`):
 * - Defines the 14 keycaps with Xiaohe final / special-initial hints.
 * - Maps ambiguous dual-key tap tokens (`Q E T U O A D G J Z C B`) and exact lowercase keys (`a-z`).
 * - Matches valid Mandarin Xiaohe 2-key syllables for inline syllable filtering & locking.
 * - Formats Rime preedit + spelling hints into readable Pinyin.
 */
object Flypy14KeyHelper {

    val KEY_ROWS: List<List<Flypy14KeySpec>> = listOf(
        listOf(
            Flypy14KeySpec('q', "iu", 'w', "ei", 'Q'),
            Flypy14KeySpec('e', "e", 'r', "uan", 'E'),
            Flypy14KeySpec('t', "ue", 'y', "un", 'T'),
            Flypy14KeySpec('u', "sh", 'i', "ch", 'U'),
            Flypy14KeySpec('o', "uo", 'p', "ie", 'O')
        ),
        listOf(
            Flypy14KeySpec('a', "a", 's', "ong·iong", 'A'),
            Flypy14KeySpec('d', "ai", 'f', "en", 'D'),
            Flypy14KeySpec('g', "eng", 'h', "ang", 'G'),
            Flypy14KeySpec('j', "an", 'k', "ing·uai", 'J'),
            Flypy14KeySpec('l', "iang·uang", null, "", 'l')
        ),
        listOf(
            Flypy14KeySpec('z', "ou", 'x', "ia·ua", 'Z'),
            Flypy14KeySpec('c', "ao", 'v', "zh·ui", 'C'),
            Flypy14KeySpec('b', "in", 'n', "iao", 'B'),
            Flypy14KeySpec('m', "ian", null, "", 'm')
        )
    )

    val AMBIGUOUS_TOKENS: Set<Char> = setOf(
        'Q', 'E', 'T', 'U', 'O',
        'A', 'D', 'G', 'J',
        'Z', 'C', 'B'
    )

    private val TOKEN_TO_LETTERS: Map<Char, List<Char>> = mapOf(
        'Q' to listOf('q', 'w'),
        'E' to listOf('e', 'r'),
        'T' to listOf('t', 'y'),
        'U' to listOf('u', 'i'),
        'O' to listOf('o', 'p'),
        'A' to listOf('a', 's'),
        'D' to listOf('d', 'f'),
        'G' to listOf('g', 'h'),
        'J' to listOf('j', 'k'),
        'Z' to listOf('z', 'x'),
        'C' to listOf('c', 'v'),
        'B' to listOf('b', 'n')
    )

    private val SINGLE_TOKEN_INITIALS: Map<Char, List<Pair<String, Char>>> = mapOf(
        'Q' to listOf("q" to 'q', "w" to 'w'),
        'E' to listOf("e" to 'e', "r" to 'r'),
        'T' to listOf("t" to 't', "y" to 'y'),
        'U' to listOf("sh" to 'u', "ch" to 'i'),
        'O' to listOf("o" to 'o', "p" to 'p'),
        'A' to listOf("a" to 'a', "s" to 's'),
        'D' to listOf("d" to 'd', "f" to 'f'),
        'G' to listOf("g" to 'g', "h" to 'h'),
        'J' to listOf("j" to 'j', "k" to 'k'),
        'Z' to listOf("z" to 'z', "x" to 'x'),
        'C' to listOf("zh" to 'v', "c" to 'c'),
        'B' to listOf("b" to 'b', "n" to 'n')
    )

    private val HIGH_FREQ_RANK: Map<String, Int> = listOf(
        "de", "shi", "bu", "wo", "ni", "yi", "le", "zai", "you", "ta", "zhe", "ge", "men", "hao",
        "ren", "zhong", "guo", "wei", "shang", "lai", "shuo", "dao", "yao", "hui", "jiu", "ke",
        "he", "dou", "ye", "hen", "qu", "kan", "neng", "xiang", "mei", "hai", "zhi", "na",
        "da", "xiao", "duo", "shao", "dui", "xia", "tian", "jin", "ming", "nian", "yue", "ri",
        "fen", "dian", "xian", "gong", "zuo", "xue", "xi", "jia", "peng", "shen", "me", "zen",
        "ma", "ba", "ne", "a", "o", "e", "en", "ai", "gei", "bei", "rang", "cong", "gen", "bi",
        "yin", "suo", "dan", "ru", "huo", "er", "bing", "qie", "ran", "hou", "qian", "li", "wai",
        "nei", "dong", "nan", "gao", "di", "chang", "duan", "xin", "lao", "kuai", "man", "kai",
        "guan", "fa", "song", "shou", "xie", "du", "ting", "wen", "hua", "zi", "ci", "ju", "pin",
        "jian", "pan", "shu", "nao", "ji", "wang", "luo", "ruan", "ying", "che", "lu", "fang",
        "shui", "feng", "yu", "qing", "gan", "si", "jue", "ming", "bai", "chu", "ti", "ban",
        "jie", "wan", "cheng", "zhun", "que", "ding", "xuan", "ze", "gai", "bian", "zeng", "shan",
        "xiu", "cha", "zhao", "ce", "yan", "zheng", "bao", "an", "zhuang", "mi", "han",
        "nv", "lv", "xu", "nve", "lve"
    ).distinct().withIndex().associate { (idx, syl) -> syl to idx }

    /**
     * Complete Xiaohe 2-letter code -> display Pinyin table (derived directly from `double_pinyin_flypy.schema.yaml`
     * over `8105.dict.yaml` + `base.dict.yaml`, covering all 414 standard syllables / 423 Xiaohe codes).
     */
    val XIAOHE_CODE_TO_PINYIN: Map<String, String> = mapOf(
        "aa" to "a", "ac" to "ao", "ad" to "ai", "ah" to "ang", "ai" to "ai", "aj" to "an", "an" to "an", "ao" to "ao",
        "ba" to "ba", "bb" to "bin", "bc" to "bao", "bd" to "bai", "bf" to "ben", "bg" to "beng", "bh" to "bang", "bi" to "bi",
        "bj" to "ban", "bk" to "bing", "bl" to "biang", "bm" to "bian", "bn" to "biao", "bo" to "bo", "bp" to "bie", "bu" to "bu",
        "bw" to "bei", "ca" to "ca", "cc" to "cao", "cd" to "cai", "ce" to "ce", "cf" to "cen", "cg" to "ceng", "ch" to "cang",
        "ci" to "ci", "cj" to "can", "co" to "cuo", "cr" to "cuan", "cs" to "cong", "cu" to "cu", "cv" to "cui", "cw" to "cei",
        "cy" to "cun", "cz" to "cou", "da" to "da", "dc" to "dao", "dd" to "dai", "de" to "de", "df" to "den", "dg" to "deng",
        "dh" to "dang", "di" to "di", "dj" to "dan", "dk" to "ding", "dm" to "dian", "dn" to "diao", "do" to "duo", "dp" to "die",
        "dq" to "diu", "dr" to "duan", "ds" to "dong", "du" to "du", "dv" to "dui", "dw" to "dei", "dx" to "dia", "dy" to "dun",
        "dz" to "dou", "ee" to "e", "ef" to "en", "eg" to "eng", "ei" to "ei", "en" to "en", "er" to "er", "ew" to "ei",
        "fa" to "fa", "ff" to "fen", "fg" to "feng", "fh" to "fang", "fj" to "fan", "fn" to "fiao", "fo" to "fo", "fu" to "fu",
        "fw" to "fei", "fz" to "fou", "ga" to "ga", "gc" to "gao", "gd" to "gai", "ge" to "ge", "gf" to "gen", "gg" to "geng",
        "gh" to "gang", "gj" to "gan", "gk" to "guai", "gl" to "guang", "go" to "guo", "gr" to "guan", "gs" to "gong", "gu" to "gu",
        "gv" to "gui", "gw" to "gei", "gx" to "gua", "gy" to "gun", "gz" to "gou", "ha" to "ha", "hc" to "hao", "hd" to "hai",
        "he" to "he", "hf" to "hen", "hg" to "heng", "hh" to "hang", "hj" to "han", "hk" to "huai", "hl" to "huang", "ho" to "huo",
        "hr" to "huan", "hs" to "hong", "hu" to "hu", "hv" to "hui", "hw" to "hei", "hx" to "hua", "hy" to "hun", "hz" to "hou",
        "ia" to "cha", "ic" to "chao", "id" to "chai", "ie" to "che", "if" to "chen", "ig" to "cheng", "ih" to "chang", "ii" to "chi",
        "ij" to "chan", "ik" to "chuai", "il" to "chuang", "io" to "chuo", "ir" to "chuan", "is" to "chong", "iu" to "chu", "iv" to "chui",
        "ix" to "chua", "iy" to "chun", "iz" to "chou", "jb" to "jin", "ji" to "ji", "jk" to "jing", "jl" to "jiang", "jm" to "jian",
        "jn" to "jiao", "jp" to "jie", "jq" to "jiu", "jr" to "juan", "js" to "jiong", "jt" to "jue", "ju" to "ju", "jv" to "ju",
        "jx" to "jia", "jy" to "jun", "ka" to "ka", "kc" to "kao", "kd" to "kai", "ke" to "ke", "kf" to "ken", "kg" to "keng",
        "kh" to "kang", "kj" to "kan", "kk" to "kuai", "kl" to "kuang", "ko" to "kuo", "kr" to "kuan", "ks" to "kong", "ku" to "ku",
        "kv" to "kui", "kw" to "kei", "kx" to "kua", "ky" to "kun", "kz" to "kou", "la" to "la", "lb" to "lin", "lc" to "lao",
        "ld" to "lai", "le" to "le", "lg" to "leng", "lh" to "lang", "li" to "li", "lj" to "lan", "lk" to "ling", "ll" to "liang",
        "lm" to "lian", "ln" to "liao", "lo" to "luo", "lp" to "lie", "lq" to "liu", "lr" to "luan", "ls" to "long", "lt" to "lve",
        "lu" to "lu", "lv" to "lv", "lw" to "lei", "lx" to "lia", "ly" to "lun", "lz" to "lou", "ma" to "ma", "mb" to "min",
        "mc" to "mao", "md" to "mai", "me" to "me", "mf" to "men", "mg" to "meng", "mh" to "mang", "mi" to "mi", "mj" to "man",
        "mk" to "ming", "mm" to "mian", "mn" to "miao", "mo" to "mo", "mp" to "mie", "mq" to "miu", "mu" to "mu", "mw" to "mei",
        "mz" to "mou", "na" to "na", "nb" to "nin", "nc" to "nao", "nd" to "nai", "ne" to "ne", "nf" to "nen", "ng" to "neng",
        "nh" to "nang", "ni" to "ni", "nj" to "nan", "nk" to "ning", "nl" to "niang", "nm" to "nian", "nn" to "niao", "no" to "nuo",
        "np" to "nie", "nq" to "niu", "nr" to "nuan", "ns" to "nong", "nt" to "nve", "nu" to "nu", "nv" to "nv", "nw" to "nei",
        "nz" to "nou", "oo" to "o", "ou" to "ou", "oz" to "ou", "pa" to "pa", "pb" to "pin", "pc" to "pao", "pd" to "pai",
        "pf" to "pen", "pg" to "peng", "ph" to "pang", "pi" to "pi", "pj" to "pan", "pk" to "ping", "pm" to "pian", "pn" to "piao",
        "po" to "po", "pp" to "pie", "pu" to "pu", "pw" to "pei", "pz" to "pou", "qb" to "qin", "qi" to "qi", "qk" to "qing",
        "ql" to "qiang", "qm" to "qian", "qn" to "qiao", "qp" to "qie", "qq" to "qiu", "qr" to "quan", "qs" to "qiong", "qt" to "que",
        "qu" to "qu", "qv" to "qu", "qx" to "qia", "qy" to "qun", "rc" to "rao", "re" to "re", "rf" to "ren", "rg" to "reng",
        "rh" to "rang", "ri" to "ri", "rj" to "ran", "ro" to "ruo", "rr" to "ruan", "rs" to "rong", "ru" to "ru", "rv" to "rui",
        "rx" to "rua", "ry" to "run", "rz" to "rou", "sa" to "sa", "sc" to "sao", "sd" to "sai", "se" to "se", "sf" to "sen",
        "sg" to "seng", "sh" to "sang", "si" to "si", "sj" to "san", "so" to "suo", "sr" to "suan", "ss" to "song", "su" to "su",
        "sv" to "sui", "sy" to "sun", "sz" to "sou", "ta" to "ta", "tc" to "tao", "td" to "tai", "te" to "te", "tg" to "teng",
        "th" to "tang", "ti" to "ti", "tj" to "tan", "tk" to "ting", "tm" to "tian", "tn" to "tiao", "to" to "tuo", "tp" to "tie",
        "tr" to "tuan", "ts" to "tong", "tu" to "tu", "tv" to "tui", "tw" to "tei", "ty" to "tun", "tz" to "tou", "ua" to "sha",
        "uc" to "shao", "ud" to "shai", "ue" to "she", "uf" to "shen", "ug" to "sheng", "uh" to "shang", "ui" to "shi", "uj" to "shan",
        "uk" to "shuai", "ul" to "shuang", "uo" to "shuo", "ur" to "shuan", "uu" to "shu", "uv" to "shui", "uw" to "shei", "ux" to "shua",
        "uy" to "shun", "uz" to "shou", "va" to "zha", "vc" to "zhao", "vd" to "zhai", "ve" to "zhe", "vf" to "zhen", "vg" to "zheng",
        "vh" to "zhang", "vi" to "zhi", "vj" to "zhan", "vk" to "zhuai", "vl" to "zhuang", "vo" to "zhuo", "vr" to "zhuan", "vs" to "zhong",
        "vu" to "zhu", "vv" to "zhui", "vw" to "zhei", "vx" to "zhua", "vy" to "zhun", "vz" to "zhou", "wa" to "wa", "wd" to "wai",
        "wf" to "wen", "wg" to "weng", "wh" to "wang", "wj" to "wan", "wo" to "wo", "wu" to "wu", "ww" to "wei", "xb" to "xin",
        "xi" to "xi", "xk" to "xing", "xl" to "xiang", "xm" to "xian", "xn" to "xiao", "xp" to "xie", "xq" to "xiu", "xr" to "xuan",
        "xs" to "xiong", "xt" to "xue", "xu" to "xu", "xv" to "xu", "xx" to "xia", "xy" to "xun", "ya" to "ya", "yb" to "yin",
        "yc" to "yao", "ye" to "ye", "yh" to "yang", "yi" to "yi", "yj" to "yan", "yk" to "ying", "yo" to "yo", "yr" to "yuan",
        "ys" to "yong", "yt" to "yue", "yu" to "yu", "yv" to "yu", "yy" to "yun", "yz" to "you", "za" to "za", "zc" to "zao",
        "zd" to "zai", "ze" to "ze", "zf" to "zen", "zg" to "zeng", "zh" to "zang", "zi" to "zi", "zj" to "zan", "zo" to "zuo",
        "zr" to "zuan", "zs" to "zong", "zu" to "zu", "zv" to "zui", "zw" to "zei", "zy" to "zun", "zz" to "zou"
    )

    fun isFlypy14InputChar(ch: Char): Boolean = ch in 'a'..'z' || ch in AMBIGUOUS_TOKENS

    fun isAmbiguousToken(ch: Char): Boolean = ch in AMBIGUOUS_TOKENS

    fun tokenToLetters(ch: Char): List<Char> = when {
        ch in TOKEN_TO_LETTERS -> TOKEN_TO_LETTERS.getValue(ch)
        ch in 'a'..'z' -> listOf(ch)
        else -> emptyList()
    }

    /**
     * Represents the first syllable slot in [rawCode] that still contains at least one ambiguous token.
     * [syllableIndex] is the 0-based Xiaohe syllable index inside [rawCode].
     */
    data class AmbiguousSlot(
        val startIndex: Int,
        val endIndexExclusive: Int,
        val rawTokens: String,
        val syllableIndex: Int
    )

    /**
     * Scans [workCode] (without any already-committed Hanzi prefix) into 2-char Xiaohe slots
     * (respecting `'` delimiters) and returns the first slot that still contains an uppercase ambiguous token.
     */
    fun findFirstAmbiguousSlot(workCode: String): AmbiguousSlot? {
        var i = 0
        var sylIdx = 0
        val n = workCode.length
        while (i < n) {
            if (workCode[i] == '\'' || workCode[i] == ' ') {
                i++
                continue
            }
            val start = i
            val first = workCode[i]
            val second = if (i + 1 < n && workCode[i + 1] != '\'' && workCode[i + 1] != ' ') {
                workCode[i + 1]
            } else {
                null
            }
            val end = if (second != null) i + 2 else i + 1
            val slice = workCode.substring(start, end)
            if (slice.any { isAmbiguousToken(it) }) {
                return AmbiguousSlot(
                    startIndex = start,
                    endIndexExclusive = end,
                    rawTokens = slice,
                    syllableIndex = sylIdx
                )
            }
            i = end
            sylIdx++
        }
        return null
    }

    /**
     * Resolves valid Pinyin syllable options for the first ambiguous slot in [tailCode].
     * Returns display Pinyin labels (e.g. `["zhong", "cong", "zha", "ca"]`) prioritized by
     * [preferredSyllable] (Rime's primary candidate hint) and Mandarin frequency.
     */
    fun matchLeadingSyllables(
        tailCode: String,
        preferredSyllable: String? = null,
        maxResults: Int = 8
    ): List<String> {
        val slot = findFirstAmbiguousSlot(tailCode) ?: return emptyList()
        val preferred = preferredSyllable?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        if (slot.rawTokens.length == 1) {
            val initials = SINGLE_TOKEN_INITIALS[slot.rawTokens[0]].orEmpty().map { it.first }
            if (initials.isEmpty()) return emptyList()
            val out = LinkedHashSet<String>()
            if (preferred != null && preferred in initials) out.add(preferred)
            out.addAll(initials)
            return out.take(maxResults)
        }

        val c0Choices = tokenToLetters(slot.rawTokens[0])
        val c1Choices = tokenToLetters(slot.rawTokens[1])
        val candidates = LinkedHashSet<String>()
        for (a in c0Choices) {
            for (b in c1Choices) {
                val code = "$a$b"
                val pinyin = XIAOHE_CODE_TO_PINYIN[code] ?: continue
                candidates.add(pinyin)
            }
        }
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedWith(
            compareBy<String> { if (it == preferred) -1 else 0 }
                .thenBy { HIGH_FREQ_RANK[it] ?: Int.MAX_VALUE }
                .thenBy { it }
        )
        return sorted.take(maxResults)
    }

    /**
     * Resolves the exact lowercase Xiaohe replacement string (`"vs"` or `"v"`) matching
     * [syllablePinyin] against [slotTokens] (1 or 2 chars).
     */
    fun resolveReplacementForSlot(syllablePinyin: String, slotTokens: String): String? {
        val clean = syllablePinyin.trim().lowercase()
        if (clean.isEmpty()) return null
        if (slotTokens.length == 1) {
            val pair = SINGLE_TOKEN_INITIALS[slotTokens[0]]?.firstOrNull { it.first == clean }
            return pair?.second?.toString()
        }
        if (slotTokens.length == 2) {
            val c0Choices = tokenToLetters(slotTokens[0])
            val c1Choices = tokenToLetters(slotTokens[1])
            // Prefer exact `u` over `v` for `ju/qu/xu/yu` if both match
            for (a in c0Choices) {
                for (b in c1Choices) {
                    val code = "$a$b"
                    if (XIAOHE_CODE_TO_PINYIN[code] == clean) {
                        return code
                    }
                }
            }
        }
        return null
    }

    /**
     * Formats Rime preedit into readable Pinyin (e.g. `"zhong guo"`, `"今天 zhong'g"`),
     * combining [pendingCommittedPrefix], any in-composition selected Chinese prefix from [rimePreedit],
     * and the top candidate's spelling hint ([topCandidateComment]).
     */
    fun formatFlypy14Preedit(
        rimePreedit: String,
        topCandidateComment: String,
        pendingCommittedPrefix: String = ""
    ): String {
        val (inCompPrefix, tailCode) = T9PinyinHelper.splitRimePreedit(rimePreedit)
        val combinedPrefix = (pendingCommittedPrefix + inCompPrefix).trim()
        val cleanComment = topCandidateComment.trim()

        val tailDisplay = when {
            tailCode.isEmpty() -> ""
            cleanComment.isNotEmpty() -> {
                val commentSyllables = cleanComment.split(' ').filter { it.isNotBlank() }
                val rawTokensCompact = tailCode.replace(" ", "").replace("'", "")
                // In Xiaohe Shuangpin each complete syllable consumes 2 input tokens.
                val matchedTokens = commentSyllables.size * 2
                if (matchedTokens < rawTokensCompact.length) {
                    val unmatchedTail = rawTokensCompact.substring(matchedTokens)
                    val formattedUnmatched = formatRawTokenTail(unmatchedTail)
                    "$cleanComment $formattedUnmatched"
                } else {
                    cleanComment
                }
            }
            else -> formatRawTokenTail(tailCode.replace(" ", ""))
        }

        return when {
            combinedPrefix.isNotEmpty() && tailDisplay.isNotEmpty() -> "$combinedPrefix $tailDisplay"
            combinedPrefix.isNotEmpty() -> combinedPrefix
            else -> tailDisplay
        }
    }

    private fun formatRawTokenTail(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder()
        for (ch in raw) {
            when {
                ch == '\'' -> sb.append('\'')
                ch in 'a'..'z' -> sb.append(ch)
                ch in AMBIGUOUS_TOKENS -> {
                    val letters = TOKEN_TO_LETTERS.getValue(ch)
                    sb.append(letters[0]).append('/').append(letters[1])
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
