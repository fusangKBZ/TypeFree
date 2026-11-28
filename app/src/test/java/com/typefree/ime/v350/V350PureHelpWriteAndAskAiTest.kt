package com.typefree.ime.v350

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.typefree.ime.bubble.TypeFreeAccessibilityService
import com.typefree.ime.bubble.ViewportOcrExtractor
import com.typefree.ime.data.AppRepository
import com.typefree.ime.data.AppSettings
import com.typefree.ime.data.DefaultPresets
import com.typefree.ime.data.HistoryRetention
import com.typefree.ime.data.InMemoryKeyValueStore
import com.typefree.ime.data.JvmAesGcmCipher
import com.typefree.ime.data.SecretVault
import com.typefree.ime.data.VoiceIntentMode
import com.typefree.ime.ime.AskAiTurn
import com.typefree.ime.ime.ImeHostCallbacks
import com.typefree.ime.ime.ImeLogic
import com.typefree.ime.ime.ImeUiAction
import com.typefree.ime.ime.ImeUiState
import com.typefree.ime.ime.KeyboardTabMode
import com.typefree.ime.ime.UndoSnapshot
import com.typefree.ime.ime.VoiceController
import com.typefree.ime.ime.VoiceSubPanel
import com.typefree.ime.ime.chrome.TopBarSlotPolicy
import com.typefree.ime.ime.jev.ChatTopology
import com.typefree.ime.ime.jev.JevDomainProfile
import com.typefree.ime.ime.jev.JevRoleMode
import com.typefree.ime.ime.panels.HelpWriteChipFilter
import com.typefree.ime.network.AiPipelineEngine
import com.typefree.ime.network.PromptBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V350PureHelpWriteAndAskAiTest {

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "app/src/main").exists()) {
            cwd
        } else {
            cwd.parentFile ?: cwd
        }
    }

    @After
    fun tearDown() {
        TypeFreeAccessibilityService.debugSimulatedNodesForTest = null
        TypeFreeAccessibilityService.debugSimulatedScreenshotBase64ForTest = null
        ViewportOcrExtractor.debugSimulatedOcrNodesForTest = null
    }

    private class FakeImeHost(
        override val aiEngine: AiPipelineEngine = AiPipelineEngine(),
        initialState: ImeUiState = ImeUiState(),
        var simulatedEditorText: String = ""
    ) : ImeHostCallbacks {
        override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        override val repository: AppRepository = AppRepository(
            appContext = null,
            publicStore = InMemoryKeyValueStore(),
            secretVault = SecretVault(
                store = InMemoryKeyValueStore(),
                cipher = JvmAesGcmCipher()
            )
        )
        override val stateFlow: MutableStateFlow<ImeUiState> = MutableStateFlow(initialState)
        val toasts = mutableListOf<String>()
        val committedTexts = mutableListOf<String>()
        val undoSnapshots = mutableListOf<UndoSnapshot>()
        var clipboardCopiedText: String? = null

        override fun currentInputConnection(): InputConnection? = null
        override fun performKeyFeedback(strong: Boolean) {}
        override fun performHaptic(strong: Boolean) {}
        override fun showQuickActionToast(message: String) {
            toasts.add(message)
        }
        override fun commitDirectText(
            text: String,
            recordUndo: Boolean,
            markManualEdit: Boolean,
            playFeedback: Boolean
        ) {
            committedTexts.add(text)
        }
        override fun pushUndoSnapshot(snapshot: UndoSnapshot?) {
            if (snapshot != null) undoSnapshots.add(snapshot)
        }
        override fun peekTopUndoSnapshot(): UndoSnapshot? = undoSnapshots.lastOrNull()
        override fun recordManualEditTouch() {}
        override fun tryAutoLearnFromRecentEdit() {}
        override fun onDictationCommittedForAutoLearn(fullBufferAfterCommit: String) {}
        override fun clearAutoLearnBaseline() {}
        override fun readSelectedTextSafely(): String = ""
        override fun readSurroundingBeforeCursor(maxChars: Int): String = ""
        override fun readFullEditorTextSafely(): String = simulatedEditorText
        override fun readSystemClipboardText(): String = clipboardCopiedText.orEmpty()
        override fun syncSystemClipboardIfNeeded(allowFreshBanner: Boolean) {}
        override fun copyTextToClipboard(text: String) {
            clipboardCopiedText = text
        }
        override fun archiveWavForHistory(wavFile: File, retention: HistoryRetention): File = wavFile
        override fun getLastRecordingWavFile(): File = File("unused.wav")
        override fun hasRetryWavFile(): Boolean = false

        fun dispose() {
            scope.cancel()
        }
    }

    // =========================================================================
    // 升级 1 验证：帮写去回复化 + 7大创作体裁 + 静音草稿扩写
    // =========================================================================

    @Test
    fun upgrade1_helpWriteChipFilter_detectsSocialPackages_andRanksCreativeGenres() {
        val socialPackages = listOf(
            "com.xingin.xhs",
            "com.ss.android.ugc.aweme",
            "com.dianping.v1",
            "com.sina.weibo",
            "tv.danmaku.bili",
            "com.zhihu.android",
            "com.smile.gifmaker",
            "com.taobao.idlefish"
        )
        for (pkg in socialPackages) {
            assertTrue("Expected social content package: $pkg", HelpWriteChipFilter.isSocialContentPackage(pkg))
        }

        // 验证 7 大创作体裁
        assertEquals(7, HelpWriteChipFilter.creativeGenres.size)
        val genreLabels = HelpWriteChipFilter.creativeGenres.map { it.label }
        assertTrue(genreLabels.contains("🌱 种草笔记"))
        assertTrue(genreLabels.contains("✨ 朋友圈文案"))
        assertTrue(genreLabels.contains("💬 高赞神评"))
        assertTrue(genreLabels.contains("📢 群通知/公告"))
        assertTrue(genreLabels.contains("🙏 请假/求助"))
        assertTrue(genreLabels.contains("📝 周报总结"))
        assertTrue(genreLabels.contains("✉️ 商务邮件"))

        // 内容社区包自动置顶 🌱 种草笔记、💬 高赞神评
        val xhsGenres = HelpWriteChipFilter.resolveCreativeGenres("com.xingin.xhs")
        assertEquals("genre_seed_note", xhsGenres[0].id)
        assertEquals("genre_top_comment", xhsGenres[1].id)

        // IM/办公包自动置顶 ✨ 朋友圈文案、📢 群通知/公告
        val wechatGenres = HelpWriteChipFilter.resolveCreativeGenres("com.tencent.mm")
        assertEquals("genre_moments", wechatGenres[0].id)
        assertEquals("genre_group_announcement", wechatGenres[1].id)

        // 6 大免开口一键成稿动作芯片（含「生成回复」）
        assertEquals(6, HelpWriteChipFilter.draftTransformPresets.size)
        assertEquals("transform_generate_reply", HelpWriteChipFilter.draftTransformPresets[0].id)
        val draftLabels = HelpWriteChipFilter.draftTransformPresets.map { it.label }
        assertTrue(draftLabels.contains("💬 生成回复"))
        assertTrue(draftLabels.contains("🌱 扩成种草文 ⚡"))
        assertTrue(draftLabels.contains("✨ 润色得体"))
        assertTrue(draftLabels.contains("👔 职场专业"))
        assertTrue(draftLabels.contains("✂️ 精简短句"))
        assertTrue(draftLabels.contains("🔥 生成吸睛标题"))

        // 旧版 API 兼容性保证
        val legacyVisible = HelpWriteChipFilter.resolveVisiblePresets(false, "com.google.android.gm", false)
        assertTrue(legacyVisible.any { it.first.contains("工作简报") })
    }

    @Test
    fun upgrade1_voiceController_pureHelpWrite_stripsChatContextCapture_andSyncsEditorDraft() {
        val host = FakeImeHost(
            initialState = ImeUiState(
                targetPackage = "com.xingin.xhs",
                tabMode = KeyboardTabMode.QWERTY
            ),
            simulatedEditorText = "富士新机真香，直出色彩绝了"
        )
        val controller = VoiceController(
            host = host,
            audioRecorderProvider = { throw UnsupportedOperationException() },
            onExternalCommitToEngine = {},
            onResetComposing = {}
        )

        controller.openOrRestoreVoicePenPanel()

        val state = host.stateFlow.value
        assertEquals(KeyboardTabMode.VOICE, state.tabMode)
        assertEquals(VoiceSubPanel.HELP_ME_WRITE, state.voiceSubPanel)
        assertFalse("Must never capture chat context in pure help write", state.capturedChatContext.hasContext)
        assertFalse("Must not prompt chat disclosure in pure help write", state.showChatContextDisclosurePrompt)
        assertEquals("富士新机真香，直出色彩绝了", state.currentEditorText)

        host.dispose()
    }

    @Test
    fun upgrade1_promptBuilder_buildHelpMeWritePrompts_injectsEditorDraftXml() {
        val (sysPrompt, userPrompt) = PromptBuilder.buildHelpMeWritePrompts(
            voiceInstruction = "扩写详细点，加几个标签",
            style = DefaultPresets.defaultStyles().first(),
            targetAppName = "小红书",
            dictionary = emptyList(),
            surroundingBeforeCursor = "",
            editorDraft = "草莓熊联名保温杯颜值天花板"
        )
        assertTrue(PromptBuilder.HELP_ME_WRITE_SYSTEM_PROMPT.contains("<editor_draft>"))
        assertEquals(PromptBuilder.HELP_ME_WRITE_SYSTEM_PROMPT, sysPrompt)
        assertTrue(userPrompt.contains("<editor_draft>"))
        assertTrue(userPrompt.contains("草莓熊联名保温杯颜值天花板"))
        assertTrue(userPrompt.contains("扩写详细点，加几个标签"))
    }

    // =========================================================================
    // 升级 2 验证：顶栏 [🔍 问AI] 胶囊 + AskAiCardPanel + 移除 == 回车拦截
    // =========================================================================

    @Test
    fun upgrade2_shouldShowTopBarAskAiPill_strictAntiDisturbanceRules() {
        val enabledSettings = AppSettings(equalEqualAskAiEnabled = true)
        val disabledSettings = AppSettings(equalEqualAskAiEnabled = false)

        // 1. 开关关闭时绝不显示
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = disabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "怎么查社保"
            )
        )

        // 2. 字数小于 2 时不显示
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "你"
            )
        )

        // 3. 字数 >= 2 且普通文本框时显示
        assertTrue(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "怎么查社保"
            )
        )

        // 4. 密码框防误弹
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                currentEditorText = "myPassword123"
            )
        )

        // 5. 纯数字/电话框防误弹
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_PHONE,
                currentEditorText = "13800138000"
            )
        )
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_NUMBER,
                currentEditorText = "123456"
            )
        )

        // 6. 处于输入法打字拼写 composing / 工具面板展开 / 剪贴板展开时防遮挡
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = true,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "打字中"
            )
        )
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = true,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "打字中"
            )
        )

        // 7. 搜索框 / 前往框（如桌面搜索“硬盘”、浏览器地址栏）防误弹
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "硬盘",
                currentImeOptions = EditorInfo.IME_ACTION_SEARCH
            )
        )
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarAskAiPill(
                settings = enabledSettings,
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "example.com",
                currentImeOptions = EditorInfo.IME_ACTION_GO
            )
        )
    }

    @Test
    fun topBarLayoutPlan_transientBannerTakesOverAndNarrowScreenGuardsOverflow() {
        // 1. 常规 360dp 手机宽度 + 新剪贴板粘贴条：临时接管中段，收起右侧非核心 AI 胶囊和冗余剪贴板图标
        val clipPlan = TopBarSlotPolicy.resolveQwertyBarLayout(
            availableWidthDp = 352f,
            showUndoButton = true,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = true,
            isClipboardBannerActive = true,
            wantAskAi = true,
            wantJevCopilot = true,
            wantVoicePen = true
        )
        assertTrue(clipPlan.bannerTakesOverMiddle)
        assertTrue(clipPlan.showLeftInlineBanner)
        assertFalse(clipPlan.showClipboardIconButton)
        assertFalse(clipPlan.showRightAskAi)
        assertFalse(clipPlan.showRightJevCopilot)
        assertFalse(clipPlan.showRightVoicePen)

        // 2. 极端窄分屏（仅 170dp）：剩余宽度不足 72dp 时隐藏内联横幅并保留剪贴板图标绿点，杜绝压成 `[·]`
        val ultraNarrowClip = TopBarSlotPolicy.resolveQwertyBarLayout(
            availableWidthDp = 170f,
            showUndoButton = true,
            showClipboardButton = true,
            isIncognito = true,
            hasPriorityBanner = true,
            isClipboardBannerActive = true,
            wantAskAi = false,
            wantJevCopilot = true,
            wantVoicePen = true
        )
        assertFalse(ultraNarrowClip.showLeftInlineBanner)
        assertTrue(ultraNarrowClip.showClipboardIconButton)

        // 3. 常态 352dp（无搜索框误弹，仅军师 + 帮写 + 语音）：完整展示右侧 3 胶囊 + 左侧全图标
        val steadyPlan = TopBarSlotPolicy.resolveQwertyBarLayout(
            availableWidthDp = 352f,
            showUndoButton = true,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            isClipboardBannerActive = false,
            wantAskAi = false,
            wantJevCopilot = true,
            wantVoicePen = true
        )
        assertFalse(steadyPlan.bannerTakesOverMiddle)
        assertTrue(steadyPlan.showClipboardIconButton)
        assertTrue(steadyPlan.showRightJevCopilot)
        assertTrue(steadyPlan.showRightVoicePen)

        // 4. 常态窄屏叠加无痕 + 4 个右侧胶囊时，按优先级先降级 AskAI，避免挤爆
        val crowdedSteady = TopBarSlotPolicy.resolveQwertyBarLayout(
            availableWidthDp = 340f,
            showUndoButton = true,
            showClipboardButton = true,
            isIncognito = true,
            hasPriorityBanner = false,
            isClipboardBannerActive = false,
            wantAskAi = true,
            wantJevCopilot = true,
            wantVoicePen = true
        )
        assertFalse(crowdedSteady.showRightAskAi)
        assertTrue(crowdedSteady.showRightVoicePen)
    }

    @Test
    fun upgrade2_askAiCardActions_clearEditorAndCopyAndCommit() {
        val host = FakeImeHost(
            initialState = ImeUiState(
                tabMode = KeyboardTabMode.VOICE,
                voiceSubPanel = VoiceSubPanel.ASK_AI_CARD,
                askAiQuery = "什么是微服务架构？",
                askAiAnswer = "微服务是一种将单体应用拆分为多个独立服务、通过轻量通信协同的架构风格。"
            )
        ).apply {
            repository.updateSettings { it.copy(appLanguage = "zh-CN") }
        }
        val controller = VoiceController(
            host = host,
            audioRecorderProvider = { throw UnsupportedOperationException() },
            onExternalCommitToEngine = {},
            onResetComposing = {}
        )

        // 1. 复制答案
        controller.copyAskAiAnswer()
        assertEquals("微服务是一种将单体应用拆分为多个独立服务、通过轻量通信协同的架构风格。", host.clipboardCopiedText)
        assertTrue(host.toasts.any { it.contains("已复制") })

        // 2. 知道了（清空输入框），压入撤回快照并切回 QWERTY
        controller.dismissAskAiAndClearEditor()
        assertEquals(KeyboardTabMode.QWERTY, host.stateFlow.value.tabMode)
        assertEquals(VoiceSubPanel.NONE, host.stateFlow.value.voiceSubPanel)
        assertEquals("", host.stateFlow.value.currentEditorText)
        assertEquals(1, host.undoSnapshots.size)
        assertEquals("什么是微服务架构？", host.undoSnapshots[0].replacedOriginalText)

        // 3. 填入输入框
        host.stateFlow.value = host.stateFlow.value.copy(
            tabMode = KeyboardTabMode.VOICE,
            voiceSubPanel = VoiceSubPanel.ASK_AI_CARD,
            askAiQuery = "什么是微服务？",
            askAiAnswer = "服务拆分与轻量通信架构"
        )
        controller.commitAskAiAnswerToEditor()
        assertEquals(KeyboardTabMode.QWERTY, host.stateFlow.value.tabMode)
        assertEquals("服务拆分与轻量通信架构", host.stateFlow.value.currentEditorText)

        host.dispose()
    }

    @Test
    fun upgrade2_promptBuilder_askAiCardSystemPrompt_is100PercentStatic() {
        val (sysPrompt, userPrompt) = PromptBuilder.buildAskAiCardPrompts(
            query = "中国空间站最新进展",
            targetAppName = "浏览器"
        )
        assertEquals(PromptBuilder.ASK_AI_CARD_SYSTEM_PROMPT, sysPrompt)
        assertTrue(sysPrompt.contains("180 字以内"))
        assertTrue(sysPrompt.contains("严禁输出 `**`、`###`"))
        assertTrue(userPrompt.contains("<user_query>"))
        assertTrue(userPrompt.contains("中国空间站最新进展"))
    }

    // =========================================================================
    // 升级 3 验证：Jev 军师五层决策树 + Layer 3 事实筛子与认知平视
    // =========================================================================

    @Test
    fun upgrade3_jevCopilotSystemPrompt_enforcesEpistemicSieve_andRetainsAllAnchors() {
        val prompt = PromptBuilder.JEV_COPILOT_SYSTEM_PROMPT

        // 1. 事实专业度筛子与二元分流核心规则
        assertTrue("Must declare Layer 3 Epistemic Sieve", prompt.contains("议题性质二元分流与事实专业度筛子"))
        assertTrue("Must include Path E epistemic discussion", prompt.contains("路径 E：认知/技术/观点探讨局"))
        assertTrue("Must include Path P interpersonal dynamics", prompt.contains("路径 P：人际/利益/情绪社交局"))
        assertTrue("Must mention fact sieve", prompt.contains("事实与专业度筛子"))
        assertTrue("Must forbid playing dumb", prompt.contains("封杀“装蠢捧哏提问”与“陈年谐音烂梗”"))

        // 2. 内行认知平视三轴
        assertTrue("Must include Strategy 1 Elevate & Ground", prompt.contains("同频升维 · 补齐本质"))
        assertTrue("Must include Strategy 2 Gentle Erratum", prompt.contains("温和勘误 · 反例破局"))
        assertTrue("Must include Strategy 3 Geek Extrapolation", prompt.contains("极客外推 · 逻辑冷幽默"))

        // 3. 完整保留所有既有单测锚点短语（确保 V331/V343/V330 绿灯）
        assertTrue(prompt.contains("三态角色拓扑路由铁律"))
        assertTrue(prompt.contains("态势 A【1v1私聊对象】"))
        assertTrue(prompt.contains("态势 B【群聊当事人】"))
        assertTrue(prompt.contains("态势 C【群聊第三方旁观者】"))
        assertTrue(prompt.contains("再菜也是你爹"))
        assertTrue(prompt.contains("场外吃瓜/扇风递梗"))
        assertTrue(prompt.contains("<weights>W1,W2,W3</weights>"))
        assertTrue(prompt.contains("[@群友X]"))
        assertTrue(prompt.contains("协同破局"))
        assertTrue(prompt.contains("温和设限"))
        assertTrue(prompt.contains("升维重构"))
        assertTrue(prompt.contains("周全给面"))
        assertTrue(prompt.contains("松弛直给"))
        assertTrue(prompt.contains("锚定闭环"))

        // 4. Conversation topology Bystander mode dual-branch instruction
        val (_, bystanderUserPrompt) = PromptBuilder.buildJevCopilotPrompts(
            domainProfile = JevDomainProfile.IM_CHAT,
            targetAppName = "微信",
            chatContextMessages = listOf("[群友A] 大模型词距原理其实很简单"),
            chatTopology = ChatTopology(
                isGroupChat = true,
                groupMemberCount = 5,
                roleMode = JevRoleMode.GROUP_BYSTANDER
            )
        )
        assertTrue(bystanderUserPrompt.contains("机主当前角色定位：群聊第三方旁观者"))
        assertTrue(bystanderUserPrompt.contains("内行认知平视三轴【同频升维、温和勘误、极客外推】"))
    }

    // =========================================================================
    // 架构质量红线守护：KeyboardScreen.kt <= 650 行
    // =========================================================================

    @Test
    fun architectureRedline_keyboardScreenLineCountStrictlyBounded() {
        val root = resolveProjectRoot()
        val screenFile = File(root, "app/src/main/java/com/typefree/ime/ime/KeyboardScreen.kt")
        assertTrue("KeyboardScreen.kt must exist", screenFile.exists())
        val lines = screenFile.readLines().size
        assertTrue(
            "KeyboardScreen.kt must be <= 650 lines (Architecture Contract), actual: $lines",
            lines <= 650
        )

        // 验证新增面板存在且独立
        val askAiPanel = File(root, "app/src/main/java/com/typefree/ime/ime/panels/AskAiCardPanel.kt")
        assertTrue("AskAiCardPanel.kt must exist", askAiPanel.exists() && askAiPanel.length() > 0)
    }

    @Test
    fun optionA_resolveQwertyMiddleAiPills_contextAwareAndOverflowSafe() {
        // 1. Blank/short editor & no selection: middle stays completely empty
        val idleBlank = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 336f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = false,
            wantAskAi = false,
            wantJevCopilot = true
        )
        assertFalse(idleBlank.showAskAi)
        assertFalse(idleBlank.showRewrite)
        assertFalse(idleBlank.showJevCopilot)

        // 2. Idle >= 2 chars & no selection: shows [Ask AI] only
        val idleWithText = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 336f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = false,
            wantAskAi = true,
            wantJevCopilot = true
        )
        assertTrue(idleWithText.showAskAi)
        assertFalse(idleWithText.showRewrite)
        assertFalse(idleWithText.showJevCopilot)

        // 3. Active selection on 336dp standard phone width: shows [Rewrite] + [Copilot], drops [Ask AI]
        val selectionStandard = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 336f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = true,
            wantAskAi = true,
            wantJevCopilot = true
        )
        assertTrue(selectionStandard.showRewrite)
        assertTrue(selectionStandard.showJevCopilot)
        assertFalse(selectionStandard.showAskAi)

        // 4. Active selection on wide 400dp screen: shows all three
        val selectionWide = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 400f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = true,
            wantAskAi = true,
            wantJevCopilot = true
        )
        assertTrue(selectionWide.showRewrite)
        assertTrue(selectionWide.showJevCopilot)
        assertTrue(selectionWide.showAskAi)

        // 5. Priority banner active: yields all middle pills
        val bannerActive = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 400f,
            showUndoButton = false,
            showClipboardButton = false,
            isIncognito = false,
            hasPriorityBanner = true,
            hasActiveSelection = true,
            wantAskAi = true,
            wantJevCopilot = true
        )
        assertFalse(bannerActive.showRewrite)
        assertFalse(bannerActive.showJevCopilot)
        assertFalse(bannerActive.showAskAi)

        // 6. Narrow 270dp screen with selection: keeps [Rewrite], drops [Copilot] & [Ask AI]
        val narrowSelection = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 270f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = true,
            wantAskAi = true,
            wantJevCopilot = true
        )
        assertTrue(narrowSelection.showRewrite)
        assertFalse(narrowSelection.showJevCopilot)
        assertFalse(narrowSelection.showAskAi)
    }

    @Test
    fun closingAiSubPanelOrDismissingSpeakToEdit_restoresQwertyTabMode() {
        val host = FakeImeHost(
            initialState = ImeUiState(
                tabMode = KeyboardTabMode.VOICE,
                voiceSubPanel = VoiceSubPanel.JEV_COPILOT
            )
        )
        val controller = VoiceController(
            host = host,
            audioRecorderProvider = { throw UnsupportedOperationException() },
            onExternalCommitToEngine = {},
            onResetComposing = {}
        )
        try {
            controller.closeVoiceSubPanel()
            assertEquals(KeyboardTabMode.QWERTY, host.stateFlow.value.tabMode)
            assertEquals(VoiceSubPanel.NONE, host.stateFlow.value.voiceSubPanel)

            host.stateFlow.value = host.stateFlow.value.copy(
                tabMode = KeyboardTabMode.VOICE,
                voiceSubPanel = VoiceSubPanel.SPEAK_TO_EDIT,
                rawSelectedText = "selected text"
            )
            controller.dismissSpeakToEditForTurn()
            assertEquals(KeyboardTabMode.QWERTY, host.stateFlow.value.tabMode)
            assertEquals(VoiceSubPanel.NONE, host.stateFlow.value.voiceSubPanel)
            assertTrue(host.stateFlow.value.ignoreSelectionForThisTurn)
        } finally {
            host.dispose()
        }
    }

    @Test
    fun askAiCard_multiTurnPromptBuilder_injectsLast6TurnsAndKeepsStaticSystemPrompt() {
        val longHistory = (1..8).map { idx -> "问题$idx" to "回答$idx" }
        val (sysZh, userZh) = PromptBuilder.buildAskAiCardPrompts(
            query = "再举个实际例子",
            targetAppName = "微信",
            isChineseLocale = true,
            conversationHistory = longHistory
        )
        assertEquals(PromptBuilder.ASK_AI_CARD_SYSTEM_PROMPT, sysZh)
        assertTrue(userZh.contains("<conversation_history>"))
        assertFalse("Old turns beyond last 6 should be trimmed", userZh.contains("问题1"))
        assertFalse("Old turns beyond last 6 should be trimmed", userZh.contains("问题2"))
        assertTrue(userZh.contains("问题3"))
        assertTrue(userZh.contains("回答8"))
        assertTrue(userZh.contains("再举个实际例子"))

        val (sysEn, userEn) = PromptBuilder.buildAskAiCardPrompts(
            query = "Give an example",
            isChineseLocale = false,
            conversationHistory = listOf("What is Kotlin?" to "A JVM language.")
        )
        assertEquals(PromptBuilder.ASK_AI_CARD_SYSTEM_PROMPT_EN, sysEn)
        assertTrue(userEn.contains("<conversation_history>"))
        assertTrue(userEn.contains("What is Kotlin?"))
    }

    @Test
    fun askAiCard_multiTurnFollowUpAndReset_preservesAndClearsHistorySafely() {
        val host = FakeImeHost(
            initialState = ImeUiState(
                tabMode = KeyboardTabMode.VOICE,
                voiceSubPanel = VoiceSubPanel.ASK_AI_CARD,
                currentEditorText = "什么是协程",
                askAiQuery = "什么是协程",
                askAiAnswer = "协程是轻量级并发框架。",
                askAiHistory = listOf(AskAiTurn(query = "什么是协程", answer = "协程是轻量级并发框架。"))
            )
        )
        val controller = VoiceController(
            host = host,
            audioRecorderProvider = { throw UnsupportedOperationException() },
            onExternalCommitToEngine = {},
            onResetComposing = {}
        )
        try {
            // 1. Start follow-up: clears editor with undo snapshot, arms follow-up, keeps history, returns to QWERTY
            controller.startAskAiFollowUp()
            val afterArm = host.stateFlow.value
            assertEquals(KeyboardTabMode.QWERTY, afterArm.tabMode)
            assertEquals(VoiceSubPanel.NONE, afterArm.voiceSubPanel)
            assertTrue(afterArm.askAiFollowUpArmed)
            assertEquals(1, afterArm.askAiHistory.size)
            assertEquals("", afterArm.currentEditorText)
            assertEquals(1, host.undoSnapshots.size)

            // 2. Quick follow-up appends turn 2 and starts loading without resetting turn 1
            controller.sendAskAiQuickFollowUp("举个例子")
            val afterQuick = host.stateFlow.value
            assertEquals(KeyboardTabMode.VOICE, afterQuick.tabMode)
            assertEquals(VoiceSubPanel.ASK_AI_CARD, afterQuick.voiceSubPanel)
            assertFalse(afterQuick.askAiFollowUpArmed)
            assertEquals(2, afterQuick.askAiHistory.size)
            assertEquals("什么是协程", afterQuick.askAiHistory[0].query)
            assertEquals("举个例子", afterQuick.askAiHistory[1].query)
            assertTrue(
                "Turn 2 must either be loading or already settled with API-key error",
                afterQuick.isAskAiLoading || afterQuick.askAiErrorMessage != null
            )

            // 3. Reset clears all conversation turns and returns cleanly to QWERTY
            controller.resetAskAiConversation()
            val afterReset = host.stateFlow.value
            assertEquals(KeyboardTabMode.QWERTY, afterReset.tabMode)
            assertEquals(VoiceSubPanel.NONE, afterReset.voiceSubPanel)
            assertTrue(afterReset.askAiHistory.isEmpty())
            assertFalse(afterReset.askAiFollowUpArmed)
            assertEquals("", afterReset.askAiQuery)
            assertFalse(afterReset.isAskAiLoading)
        } finally {
            host.dispose()
        }
    }

    @Test
    fun xiaomiFix_helpWriteDraftFallback_and_topBarHelpWriteBesideAskAi() {
        // 1. Even if readFullEditorTextSafely() returns blank (e.g. HyperOS custom editor),
        // openOrRestoreVoicePenPanel preserves pre-synced currentEditorText so Mode B + "生成回复" shows.
        val host = FakeImeHost(
            initialState = ImeUiState(
                tabMode = KeyboardTabMode.QWERTY,
                currentEditorText = "今晚一起吃饭吗"
            ),
            simulatedEditorText = ""
        )
        val controller = VoiceController(
            host = host,
            audioRecorderProvider = { throw UnsupportedOperationException() },
            onExternalCommitToEngine = {},
            onResetComposing = {}
        )
        try {
            controller.openOrRestoreVoicePenPanel()
            assertEquals("今晚一起吃饭吗", host.stateFlow.value.currentEditorText)
            assertEquals(VoiceSubPanel.HELP_ME_WRITE, host.stateFlow.value.voiceSubPanel)
        } finally {
            host.dispose()
        }

        // 2. Both HelpWriteChipFilter.generateReplyPreset and ImeLogic.quickEditChips expose "💬 生成回复"
        assertEquals("transform_generate_reply", HelpWriteChipFilter.generateReplyPreset.id)
        assertEquals("generate_reply", ImeLogic.quickEditChips.first().id)

        // 3. When editor has >= 2 chars, both [Ask AI] and [Help Write] appear side by side in middle lane
        assertTrue(
            TopBarSlotPolicy.shouldShowTopBarHelpWritePill(
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "今晚一起吃饭吗"
            )
        )
        assertFalse(
            TopBarSlotPolicy.shouldShowTopBarHelpWritePill(
                activeTab = KeyboardTabMode.QWERTY,
                isComposing = false,
                isToolPanelOpen = false,
                isClipboardOpen = false,
                currentInputType = InputType.TYPE_CLASS_TEXT,
                currentEditorText = "你"
            )
        )
        val sideBySide = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 336f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = false,
            wantAskAi = true,
            wantJevCopilot = true,
            wantHelpWrite = true
        )
        assertTrue(sideBySide.showAskAi)
        assertTrue(sideBySide.showHelpWrite)
        assertFalse(sideBySide.showRewrite)

        // 4. Narrow screen drops secondary HelpWrite pill first while keeping Ask AI
        val narrowIdle = TopBarSlotPolicy.resolveQwertyMiddleAiPills(
            availableWidthDp = 270f,
            showUndoButton = false,
            showClipboardButton = true,
            isIncognito = false,
            hasPriorityBanner = false,
            hasActiveSelection = false,
            wantAskAi = true,
            wantJevCopilot = true,
            wantHelpWrite = true
        )
        assertTrue(narrowIdle.showAskAi)
        assertFalse(narrowIdle.showHelpWrite)
    }
}
