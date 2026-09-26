package dev.shadowcrawler.flashcards.ui.laya

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Verifies the hand-ported [LayaTokenizer] (BPE merge loop, added-tokens trie, byte fallback)
 * produces the exact same token ids as the real `tokenizers` 0.23.2 Python reference encoding
 * the identical `tokenizer.json` — this is the port's highest-risk file (a subtle merge-order or
 * whitespace-stripping bug would silently shift ids and corrupt every downstream embedding
 * lookup without throwing). Golden ids below were generated once via:
 *   `tokenizers.Tokenizer.from_file(path).encode(text, add_special_tokens=False).ids`
 * Skips (doesn't fail) when the 34 MB tokenizer.json isn't available locally — set
 * `-Dlaya.tokenizerJson=/path/to/tokenizer.json` to run it for real.
 */
class LayaTokenizerPortTest {

    private data class Case(val text: String, val expectedIds: IntArray)

    private val cases = listOf(
        Case(
            "choice question: Does spoken_answer correctly demonstrate the knowledge described in correct_answer?",
            intArrayOf(6241, 2872, 235292, 11188, 22230, 235298, 13072, 16105, 22101, 573, 5567, 6547, 575, 5112, 235298, 13072, 235336)
        ),
        Case(
            " correct: spoken_answer captures the same core idea",
            intArrayOf(5112, 235292, 22230, 235298, 13072, 59188, 573, 1809, 8131, 4268)
        ),
        Case(
            "something that matches against the fields set in the spec or manifest",
            intArrayOf(2775, 674, 15483, 2691, 573, 7969, 1142, 575, 573, 1489, 689, 17361)
        ),
        Case(
            "A query mechanism that filters Kubernetes resources by resource fields.",
            intArrayOf(586, 8164, 15613, 674, 21863, 100173, 6336, 731, 6537, 7969, 235265)
        ),
        Case(
            "同じ支払いが二重に請求されました。返金をお願いします。",
            intArrayOf(235248, 23631, 88560, 79045, 217491, 235400, 133074, 65612, 235362, 236572, 235854, 119920, 9178, 235362)
        ),
        Case(
            "  leading and trailing spaces  ",
            intArrayOf(235248, 8133, 578, 60732, 14130, 235248, 235248)
        ),
        Case(
            "tabs\tand\nnewlines\r\n",
            intArrayOf(42607, 226, 639, 108, 888, 5448, 235316, 108)
        ),
        Case(
            "emoji test 🚀 and punctuation!?;:",
            intArrayOf(52810, 2121, 167340, 578, 94152, 29675, 98043)
        ),
        Case("", intArrayOf()),
        Case("a", intArrayOf(476)),
    )

    @Test
    fun encodeMatchesPythonReference() {
        val path = System.getProperty("laya.tokenizerJson").orEmpty()
        assumeTrue(
            "Set -Dlaya.tokenizerJson=/path/to/tokenizer.json to run this port-parity check",
            path.isNotBlank() && File(path).isFile
        )
        val tokenizer = LayaTokenizer(File(path))
        for (case in cases) {
            val actual = tokenizer.encode(case.text, addSpecialTokens = false)
            assertArrayEquals(
                "encode(${case.text.take(40)}...) id mismatch",
                case.expectedIds,
                actual
            )
        }
    }
}
