package com.typefree.ime.ime

import com.typefree.ime.data.InputScheme
import com.typefree.ime.ime.engine.EngineResult
import com.typefree.ime.ime.engine.Flypy14KeyHelper
import com.typefree.ime.ime.engine.KeyInput
import com.typefree.ime.ime.engine.RimeDataManager
import com.typefree.ime.ime.engine.RimeInputEngine
import com.typefree.rime.RimeBridge
import com.typefree.rime.RimeCandidate
import com.typefree.rime.RimeContextSnapshot
import com.typefree.rime.RimeSessionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Flypy14KeyTest {

    private class FakeFlypy14RimeClient : RimeSessionClient {
        override var activeSchemaId: String = "double_pinyin_flypy_14"
        override var pageSize: Int = 24
        override val isNativeLoaded: Boolean = false
        override var isDeployed: Boolean = true
        override var wordCount: Int = 18000

        private val buffer = StringBuilder()

        private val fixtures: Map<String, Pair<String, List<RimeCandidate>>> = mapOf(
            "CA" to ("CA" to listOf(
                RimeCandidate("中", "zhong"),
                RimeCandidate("从", "cong"),
                RimeCandidate("扎", "zha"),
                RimeCandidate("擦", "ca")
            )),
            "CAGO" to ("CA GO" to listOf(
                RimeCandidate("中国", "zhong guo"),
                RimeCandidate("从过", "cong guo")
            )),
            "vs'GO" to ("vs'GO" to listOf(
                RimeCandidate("中国", "zhong guo"),
                RimeCandidate("钟锅", "zhong guo")
            )),
            "vs'go" to ("vs'go" to listOf(
                RimeCandidate("中国", "zhong guo")
            )),
            "vA" to ("vA" to listOf(
                RimeCandidate("中", "zhong"),
                RimeCandidate("扎", "zha")
            )),
            "lO" to ("lO" to listOf(
                RimeCandidate("落", "luo"),
                RimeCandidate("列", "lie")
            ))
        )

        override fun initialize(sharedDir: String, userDir: String): Boolean = true
        override fun deploy(schemaId: String): Boolean = true
        override fun selectSchema(schemaId: String): Boolean {
            activeSchemaId = schemaId
            return true
        }

        override fun processKey(keyCode: Int, mask: Int): Boolean {
            if (keyCode == RimeBridge.XK_BackSpace) {
                if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
                return true
            }
            buffer.append(keyCode.toChar())
            return true
        }

        override fun processKeyText(keys: String): RimeContextSnapshot {
            buffer.clear()
            buffer.append(keys)
            return getContext(pageSize)
        }

        override fun getContext(maxCandidates: Int): RimeContextSnapshot {
            val raw = buffer.toString()
            val hit = fixtures[raw]
            return RimeContextSnapshot(
                preedit = hit?.first ?: raw,
                rawInput = raw,
                candidates = hit?.second.orEmpty().take(maxCandidates)
            )
        }

        override fun selectCandidate(index: Int): String? {
            val picked = getContext(pageSize).candidates.getOrNull(index)?.text
            if (picked != null) buffer.clear()
            return picked
        }

        override fun commitComposition(): String? {
            val first = getContext(pageSize).candidates.firstOrNull()?.text
            buffer.clear()
            return first
        }

        override fun getCommit(): String? = null
        override fun clearComposition() {
            buffer.clear()
        }

        override fun setOption(option: String, value: Boolean) {}
        override fun getOption(option: String): Boolean = false
        override fun syncUserData(): Boolean = true
        override fun getVersion(): String = "fake-1.17.0"
    }

    @Test
    fun keyLayout_coversAll26LettersOnce_withXiaoheHints_and14KeysTotal() {
        val allSpecs = Flypy14KeyHelper.KEY_ROWS.flatten()
        assertEquals(14, allSpecs.size)
        assertEquals(listOf(5, 5, 4), Flypy14KeyHelper.KEY_ROWS.map { it.size })

        val coveredLetters = mutableListOf<Char>()
        val dualTokens = mutableSetOf<Char>()
        for (spec in allSpecs) {
            coveredLetters.add(spec.leftChar)
            assertTrue("Left hint missing for ${spec.leftChar}", spec.leftHint.isNotBlank())
            if (spec.isDual) {
                coveredLetters.add(spec.rightChar!!)
                assertTrue("Right hint missing for ${spec.rightChar}", spec.rightHint.isNotBlank())
                assertTrue("Duplicate dual tap token ${spec.tapToken}", dualTokens.add(spec.tapToken))
            } else {
                assertTrue(spec.leftChar in setOf('l', 'm'))
                assertEquals(spec.leftChar, spec.tapToken)
            }
        }
        assertEquals(26, coveredLetters.size)
        assertEquals(('a'..'z').toSet(), coveredLetters.toSet())
        assertEquals(12, dualTokens.size)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "~", "!", "@", "?"),
            allSpecs.map { it.topSymbol }
        )
    }

    @Test
    fun xiaoheMapAndSyllableMatcher_handlesZeroInitialsMixedSwipesAndSoloKeys() {
        assertEquals("a", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["aa"])
        assertEquals("ai", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["ad"])
        assertEquals("ai", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["ai"])
        assertEquals("an", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["aj"])
        assertEquals("ang", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["ah"])
        assertEquals("er", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["er"])
        assertEquals("zhong", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["vs"])
        assertEquals("nve", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["nt"])
        assertEquals("lv", Flypy14KeyHelper.XIAOHE_CODE_TO_PINYIN["lv"])

        val ca = Flypy14KeyHelper.matchLeadingSyllables("CA", preferredSyllable = "zhong")
        assertEquals("zhong", ca.first())
        assertTrue(ca.containsAll(listOf("zhong", "cong", "zha", "ca")))

        val mixedLeftSwipe = Flypy14KeyHelper.matchLeadingSyllables("vA")
        assertEquals(setOf("zhong", "zha"), mixedLeftSwipe.toSet())

        val soloL = Flypy14KeyHelper.matchLeadingSyllables("lO")
        assertTrue(soloL.containsAll(listOf("luo", "lie")))

        val singleC = Flypy14KeyHelper.matchLeadingSyllables("C")
        assertEquals(listOf("zh", "c"), singleC)

        // Zero-initial `ai` disambiguation picks `ad` for AD and `ai` for AU
        assertEquals("ad", Flypy14KeyHelper.resolveReplacementForSlot("ai", "AD"))
        assertEquals("ai", Flypy14KeyHelper.resolveReplacementForSlot("ai", "AU"))

        // Skips already-exact 2-letter prefix `vs` and targets `GO`
        val slot = Flypy14KeyHelper.findFirstAmbiguousSlot("vsGO")
        assertEquals(2, slot?.startIndex)
        assertEquals("GO", slot?.rawTokens)
        assertEquals(1, slot?.syllableIndex)
    }

    @Test
    fun formatFlypy14Preedit_rendersCleanPinyinAndTrailingPartialToken() {
        assertEquals(
            "zhong guo",
            Flypy14KeyHelper.formatFlypy14Preedit(rimePreedit = "CA GO", topCandidateComment = "zhong guo")
        )
        assertEquals(
            "今天 zhong g/h",
            Flypy14KeyHelper.formatFlypy14Preedit(rimePreedit = "今天vs'G", topCandidateComment = "zhong")
        )
    }

    @Test
    fun rimeInputEngine_flypy14TapSwipeLockAndBackspaceRollback() {
        val client = FakeFlypy14RimeClient()
        val engine = RimeInputEngine(
            rimeBridge = client,
            initialScheme = InputScheme.RIME_DOUBLE_PINYIN_FLYPY_14,
            externalEnhancedSchemePredicate = { false }
        )
        assertEquals("double_pinyin_flypy_14", client.activeSchemaId)

        // Tap C, A, G, O
        for (ch in "CAGO") {
            engine.onKey(KeyInput.CharKey(ch))
        }
        val state1 = engine.state.value
        assertTrue(state1.isComposing)
        assertEquals("中国", state1.candidates.firstOrNull()?.text)
        assertEquals("zhong guo", state1.preedit)
        assertTrue("zhong" in state1.t9Syllables)

        // Lock first ambiguous slot to "zhong" -> "vs'GO"
        engine.lockT9Syllable("zhong")
        val stateLocked = engine.state.value
        assertEquals("vs'GO", stateLocked.rawCode)
        assertTrue("guo" in stateLocked.t9Syllables)

        // Backspace O, G -> reaches "vs'" (pruned to "vs"), then next backspace unlocks "vs" -> "CA"
        engine.onKey(KeyInput.Backspace())
        engine.onKey(KeyInput.Backspace())
        assertEquals("vs", engine.state.value.rawCode)
        engine.onKey(KeyInput.Backspace())
        assertEquals("CA", engine.state.value.rawCode)
        assertEquals("中", engine.state.value.candidates.firstOrNull()?.text)

        // Enter commits Chinese candidate rather than raw uppercase tokens
        val committed = engine.onKey(KeyInput.Enter) as? EngineResult.Commit
        assertEquals("中", committed?.text)
        assertFalse(engine.state.value.isComposing)
    }

    @Test
    fun schemaAsset_isPresentInRimeAssetsAndRegisteredInDataManager() {
        val schemaFile = listOf(
            File("src/main/assets/rime/double_pinyin_flypy_14.schema.yaml"),
            File("app/src/main/assets/rime/double_pinyin_flypy_14.schema.yaml")
        ).firstOrNull { it.isFile }
        assertTrue("double_pinyin_flypy_14.schema.yaml must exist", schemaFile != null)
        val text = schemaFile!!.readText(Charsets.UTF_8)
        assertTrue(text.contains("schema_id: double_pinyin_flypy_14"))
        assertTrue(text.contains("prism: double_pinyin_flypy_14"))
        assertTrue(text.contains("derive/^[cv](.)$/C$1/"))
        assertTrue(text.contains("derive/^(.)[cv]$/$1C/"))
        assertTrue("double_pinyin_flypy_14.schema.yaml" in RimeDataManager.BUILTIN_ASSET_FILES)
    }
}
