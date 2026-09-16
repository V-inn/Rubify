package com.rubify.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Runs against the real generated assets, so it checks the data as well as the code. */
class PinyinAnnotatorTest {

    private fun assertPinyin(expected: String, text: String) =
        assertEquals(text, expected, annotator.pinyinOf(text))

    @Test
    fun polyphonicCharactersFollowTheirWord() {
        assertPinyin("yín háng", "银行")
        assertPinyin("xíng rén", "行人")
        assertPinyin("zhǎng dà", "长大")
        assertPinyin("cháng chéng", "长城")
        assertPinyin("yīn yuè", "音乐")
        assertPinyin("kuài lè", "快乐")
        assertPinyin("zhòng yào", "重要")
        assertPinyin("chóng xīn", "重新")
        assertPinyin("huán shū", "还书")
        assertPinyin("huán gěi", "还给")
        assertPinyin("wǒ yào qù yín háng huán qián", "我要去银行还钱")
        assertPinyin("hái shì", "还是")
        assertPinyin("shǒu dū", "首都")
        assertPinyin("dōu shì", "都是")
        // The phrase source lists 都会 as dūhuì; it must not win here.
        assertPinyin("tā men dōu huì lái", "他们都会来")
        assertPinyin("shuì jiào", "睡觉")
        assertPinyin("jué de", "觉得")
        assertPinyin("dé dào", "得到")
        assertPinyin("liǎo jiě", "了解")
        assertPinyin("yǐ wéi", "以为")
        assertPinyin("wèi shén me", "为什么")
    }

    @Test
    fun standaloneCharactersUseTheirCommonReading() {
        assertPinyin("zhè tiáo lù hěn cháng", "这条路很长")
        assertPinyin("tā pǎo de hěn kuài", "他跑得很快")
        assertPinyin("tā gāo xìng de shuō", "他高兴地说")
        assertPinyin("wǒ chī le", "我吃了")
        // Citation tone: 过 is far too often a verb (过马路) to default to guo.
        assertPinyin("wǒ qù guò zhōng guó", "我去过中国")
        assertPinyin("guò mǎ lù", "过马路")
        assertPinyin("tā zhǎng de hěn gāo", "他长得很高")
    }

    @Test
    fun segmentationPicksTheLikelierSplit() {
        // 的确 (dí què) must not swallow 的 when 确认 is the word.
        assertPinyin("wǒ dí què bù zhī dào", "我的确不知道")
        assertPinyin("qǐng fā sòng nǐ de què rèn xìn xī", "请发送你的确认信息")
    }

    @Test
    fun yiAndBuKeepCitationTones() {
        assertPinyin("yī gè", "一个")
        assertPinyin("bù shì", "不是")
        assertPinyin("yī cì", "一次")
    }

    @Test
    fun readsTheTextbookPage() {
        assertPinyin(
            "nǐ kě yǐ ná zǒu zhè zhāng cài dān ， kàn kàn xià cì hái chī diǎn er shén me 。",
            "你可以拿走这张菜单，看看下次还吃点儿什么。",
        )
        assertPinyin(
            "bù yòng ná cài dān ， zài shǒu jī shàng jiù néng kàn dào",
            "不用拿菜单，在手机上就能看到",
        )
    }

    @Test
    fun nonHanziBreakRunsAndGetNoPinyin() {
        assertEquals(
            listOf(null, "jī", "ròu", "fàn", null, null),
            annotator.annotate(listOf("B", "鸡", "肉", "饭", "?", "3")),
        )
    }

    @Test
    fun dictionaryCoversRareAndMissingCharacters() {
        assertNotNull(dictionary.charReading(0x20000)) // 𠀀, Extension B
        assertEquals("líng", dictionary.charReading('〇'.code))
        assertNull(dictionary.charReading('A'.code))
        assertEquals(listOf(null), annotator.annotate(listOf("ア")))
    }

    companion object {
        private val assets = File("src/main/assets/pinyin")
        private val dictionary: PinyinDictionary by lazy {
            File(assets, "chars.tsv").bufferedReader().use { chars ->
                File(assets, "words.tsv").bufferedReader().use { words ->
                    PinyinDictionary.load(chars, words)
                }
            }
        }
        private val annotator by lazy { PinyinAnnotator(dictionary) }
    }
}
