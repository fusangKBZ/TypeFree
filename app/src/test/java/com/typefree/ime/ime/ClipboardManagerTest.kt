package com.typefree.ime.ime

import com.typefree.ime.data.AppRepository
import com.typefree.ime.data.AppSettings
import com.typefree.ime.data.ClipboardEntry
import com.typefree.ime.data.InMemoryKeyValueStore
import com.typefree.ime.data.JvmAesGcmCipher
import com.typefree.ime.data.SecretVault
import com.typefree.ime.ime.clipboard.ClipboardFilterTab
import com.typefree.ime.ime.clipboard.ClipboardSmartExtractor
import com.typefree.ime.ime.clipboard.SmartClipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardManagerTest {

    @Test
    fun `extracts 6-digit OTP from Chinese SMS while skipping year prefix`() {
        val sms = "【飞书】2026年安全提醒：您的登录验证码是 638291，5分钟内有效，请勿泄露。"
        val extracted = ClipboardSmartExtractor.extractPrimary(sms)
        assertNotNull(extracted)
        assertEquals(SmartClipKind.OTP_CODE, extracted?.kind)
        assertEquals("638291", extracted?.extractedValue)
        assertEquals("🔢 仅填 638291", extracted?.chipLabel)
    }

    @Test
    fun `extracts English verification code`() {
        val msg = "Your GitHub authentication passcode is 904812. Do not share it."
        val extracted = ClipboardSmartExtractor.extractPrimary(msg)
        assertNotNull(extracted)
        assertEquals(SmartClipKind.OTP_CODE, extracted?.kind)
        assertEquals("904812", extracted?.extractedValue)
    }

    @Test
    fun `extracts URL cleanly without trailing Chinese punctuation`() {
        val msg = "安卓小鹤官网地址：https://flypy.cc/download，点此查看教程。"
        val extracted = ClipboardSmartExtractor.extractPrimary(msg)
        assertNotNull(extracted)
        assertEquals(SmartClipKind.URL, extracted?.kind)
        assertEquals("https://flypy.cc/download", extracted?.extractedValue)
    }

    @Test
    fun `extracts email and mainland phone number`() {
        val emailMsg = "请发送简历至 dev.team@typefree.org 谢谢"
        val emailExt = ClipboardSmartExtractor.extractPrimary(emailMsg)
        assertEquals(SmartClipKind.EMAIL, emailExt?.kind)
        assertEquals("dev.team@typefree.org", emailExt?.extractedValue)

        val phoneMsg = "收件人王先生 联系电话 +86 138-0013-8000 顺丰到付"
        val phoneExt = ClipboardSmartExtractor.extractPrimary(phoneMsg)
        assertEquals(SmartClipKind.PHONE, phoneExt?.kind)
        assertEquals("13800138000", phoneExt?.extractedValue)

        assertNull(ClipboardSmartExtractor.extractPrimary("今天下午三点开产品评审会"))
    }

    @Test
    fun `filterItems separates ALL PINNED and SMART tabs accurately`() {
        val entries = listOf(
            ClipboardEntry(id = "1", text = "北京市海淀区中关村软件园", timestamp = 300L, isPinned = true),
            ClipboardEntry(id = "2", text = "【顺丰】取件码 884216", timestamp = 200L, isPinned = false),
            ClipboardEntry(id = "3", text = "普通临时备注文本", timestamp = 100L, isPinned = false)
        )

        assertEquals(3, ClipboardSmartExtractor.filterItems(entries, ClipboardFilterTab.ALL).size)
        assertEquals(
            listOf("1"),
            ClipboardSmartExtractor.filterItems(entries, ClipboardFilterTab.PINNED).map { it.id }
        )
        assertEquals(
            listOf("2"),
            ClipboardSmartExtractor.filterItems(entries, ClipboardFilterTab.SMART).map { it.id }
        )
    }

    @Test
    fun `upsertClipboardList deduplicates preserves pin state and caps unpinned at 200`() {
        var list = emptyList<ClipboardEntry>()

        // Seed 2 pinned items
        val (afterFirst, p1) = AppRepository.upsertClipboardList(list, "常用地址A", forcePinned = true, nowMs = 100L)
        assertNotNull(p1)
        assertEquals("常用地址A", p1?.text)
        list = afterFirst
        list = AppRepository.upsertClipboardList(list, "常用邮箱B", forcePinned = true, nowMs = 200L).first

        // Re-copying an already pinned item with forcePinned=false MUST preserve its isPinned=true status!
        list = AppRepository.upsertClipboardList(list, "常用地址A", forcePinned = false, nowMs = 300L).first
        val pinnedA = list.first { it.text == "常用地址A" }
        assertTrue(pinnedA.isPinned)
        assertEquals(300L, pinnedA.timestamp)

        // Add 205 unpinned items -> unpinned should cap at 200, while all 2 pinned items remain at the top!
        for (i in 1..205) {
            list = AppRepository.upsertClipboardList(
                existing = list,
                rawText = "临时复制条目 #$i",
                forcePinned = false,
                nowMs = 1000L + i
            ).first
        }

        assertEquals(202, list.size)
        assertEquals(2, list.count { it.isPinned })
        assertEquals(200, list.count { !it.isPinned })
        assertTrue(list[0].isPinned && list[1].isPinned)
        assertEquals("临时复制条目 #205", list[2].text)
    }

    @Test
    fun `shouldAcceptSystemClipboardSnapshot allows explicit re-copy of deleted text while blocking stale polls`() {
        // Passive poll with same text and same timestamp must not resurrect deleted item
        assertFalse(
            AppRepository.shouldAcceptSystemClipboardSnapshot(
                normalizedText = "验证码 123456",
                clipTimestampMs = 1000L,
                lastObservedText = "验证码 123456",
                lastObservedTimestampMs = 1000L,
                fromSystemChangeEvent = false
            )
        )
        // Explicit system OnPrimaryClipChangedListener event MUST accept identical re-copied text
        assertTrue(
            AppRepository.shouldAcceptSystemClipboardSnapshot(
                normalizedText = "验证码 123456",
                clipTimestampMs = 2000L,
                lastObservedText = "验证码 123456",
                lastObservedTimestampMs = 1000L,
                fromSystemChangeEvent = true
            )
        )
        // Passive poll with newer Android clipboard timestamp also accepts re-copied text
        assertTrue(
            AppRepository.shouldAcceptSystemClipboardSnapshot(
                normalizedText = "验证码 123456",
                clipTimestampMs = 2000L,
                lastObservedText = "验证码 123456",
                lastObservedTimestampMs = 1000L,
                fromSystemChangeEvent = false
            )
        )
    }

    @Test
    fun `three clipboard buckets are decoupled with forward-only sync and independent manual entry`() {
        val store = InMemoryKeyValueStore()
        val repo = AppRepository(
            appContext = null,
            publicStore = store,
            secretVault = SecretVault(InMemoryKeyValueStore(), JvmAesGcmCipher())
        )

        // 1. Copy SMS OTP into ALL -> forward-syncs to SMART, does not touch PINNED
        val allSms = repo.recordClipboardText("【快递】您的验证码是 884210", pinned = false, nowMs = 100L)
        assertNotNull(allSms)
        assertEquals(1, repo.clipboardItems.value.size)
        assertEquals(1, repo.clipboardSmartItems.value.size)
        assertTrue(repo.clipboardPinnedItems.value.isEmpty())

        // 2. Manual add to PINNED & SMART -> stored independently without adding to ALL
        val manualPinned = repo.addPinnedClipboardPhrase("上海市浦东新区张江路88号", nowMs = 200L)
        assertNotNull(manualPinned)
        val manualSmart = repo.addSmartClipboardEntry("https://example.com/auth", nowMs = 210L)
        assertNotNull(manualSmart)
        assertEquals(1, repo.clipboardItems.value.size) // ALL unchanged!
        assertEquals(1, repo.clipboardPinnedItems.value.size)
        assertEquals(2, repo.clipboardSmartItems.value.size)

        // 3. Forward-copy from ALL -> PINNED creates an independent copy in PINNED
        repo.copyAllClipboardItemToPinned(allSms!!.id)
        assertEquals(2, repo.clipboardPinnedItems.value.size)
        assertEquals(1, repo.clipboardItems.value.size)

        // 4. Deleting in PINNED or SMART MUST NOT affect ALL!
        val pinnedSmsId = repo.clipboardPinnedItems.value.first { it.text == allSms.text }.id
        repo.deleteClipboardItemInTab(ClipboardFilterTab.PINNED, pinnedSmsId)
        repo.clearClipboardTab(ClipboardFilterTab.SMART)
        assertEquals(1, repo.clipboardItems.value.size)
        assertEquals(1, repo.clipboardPinnedItems.value.size)
        assertTrue(repo.clipboardSmartItems.value.isEmpty())

        // 5. Deleting ALL item MUST NOT affect manual PINNED item
        repo.deleteClipboardItemInTab(ClipboardFilterTab.ALL, allSms.id)
        assertTrue(repo.clipboardItems.value.isEmpty())
        assertEquals(1, repo.clipboardPinnedItems.value.size)
        assertEquals("上海市浦东新区张江路88号", repo.clipboardPinnedItems.value[0].text)
    }

    @Test
    fun `toggleClipboardItemInPinned toggles pin state without deleting ALL or SMART`() {
        val store = InMemoryKeyValueStore()
        val repo = AppRepository(
            appContext = null,
            publicStore = store,
            secretVault = SecretVault(InMemoryKeyValueStore(), JvmAesGcmCipher())
        )
        val sms = repo.recordClipboardText("验证码 952711，请在 5 分钟内输入", pinned = false, nowMs = 100L)!!
        assertTrue(repo.clipboardPinnedItems.value.isEmpty())

        // 1. First tap on 📌 in ALL -> pins to PINNED (returns true)
        assertEquals(true, repo.toggleClipboardItemInPinned(sms.id))
        assertEquals(1, repo.clipboardPinnedItems.value.size)

        // 2. Second tap on 📌 in ALL -> cancels pin in PINNED (returns false), leaves ALL & SMART intact
        assertEquals(false, repo.toggleClipboardItemInPinned(sms.id))
        assertTrue(repo.clipboardPinnedItems.value.isEmpty())
        assertEquals(1, repo.clipboardItems.value.size)
        assertEquals(1, repo.clipboardSmartItems.value.size)

        // 3. Pin again, then tap 📌 on the PINNED entry itself -> removes from PINNED without touching ALL
        assertEquals(true, repo.toggleClipboardItemInPinned(sms.id))
        val pinnedEntryId = repo.clipboardPinnedItems.value.first().id
        assertEquals(false, repo.toggleClipboardItemInPinned(pinnedEntryId))
        assertTrue(repo.clipboardPinnedItems.value.isEmpty())
        assertEquals(1, repo.clipboardItems.value.size)
    }

    @Test
    fun `qwertyKeyPopupPreview defaults to false and migrates legacy true off once`() {
        assertFalse(AppSettings().qwertyKeyPopupPreview)

        val store = InMemoryKeyValueStore()
        store.putString(AppRepository.KEY_SETTINGS, """{"qwertyKeyPopupPreview":true}""")
        val firstBootRepo = AppRepository(
            appContext = null,
            publicStore = store,
            secretVault = SecretVault(InMemoryKeyValueStore(), JvmAesGcmCipher())
        )
        assertFalse(firstBootRepo.settings.value.qwertyKeyPopupPreview)

        // User can explicitly re-enable it afterward and it stays true
        firstBootRepo.updateSettings { it.copy(qwertyKeyPopupPreview = true) }
        val secondBootRepo = AppRepository(
            appContext = null,
            publicStore = store,
            secretVault = SecretVault(InMemoryKeyValueStore(), JvmAesGcmCipher())
        )
        assertTrue(secondBootRepo.settings.value.qwertyKeyPopupPreview)
    }
}
