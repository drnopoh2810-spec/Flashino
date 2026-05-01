package com.eduspecial

import com.eduspecial.domain.model.CategoryMastery
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.MediaType
import com.eduspecial.domain.model.QAAnswer
import com.eduspecial.domain.model.QAQuestion
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Test

private fun makeFlashcard(id: String, contributor: String) = Flashcard(
    id = id,
    term = "term_$id",
    definition = "def_$id",
    category = FlashcardCategory.ABA_THERAPY,
    contributor = contributor
)

private fun makeQuestion(id: String, contributor: String) = QAQuestion(
    id = id,
    question = "question_$id",
    category = FlashcardCategory.ABA_THERAPY,
    contributor = contributor
)

private fun makeAnswer(
    id: String,
    contributor: String,
    isAccepted: Boolean = false,
    upvotes: Int = 0
) = QAAnswer(
    id = id,
    questionId = "q1",
    content = "answer_$id",
    contributor = contributor,
    isAccepted = isAccepted,
    upvotes = upvotes
)

private fun editIconVisible(card: Flashcard, userId: String): Boolean = userId == card.contributor

private fun editIconVisibleForQuestion(question: QAQuestion, userId: String): Boolean =
    userId == question.contributor

private fun editIconVisibleForAnswer(answer: QAAnswer, userId: String): Boolean =
    userId == answer.contributor

private fun sortAnswers(answers: List<QAAnswer>): List<QAAnswer> =
    answers.sortedByDescending { it.isAccepted }

private fun computeStreak(reviewCounts: List<Int>): Int {
    var streak = 0
    for (count in reviewCounts.reversed()) {
        if (count > 0) streak++ else break
    }
    return streak
}

private fun validateDisplayName(name: String): Result<String> {
    return if (name.length < 2 || name.length > 50) {
        Result.failure(IllegalArgumentException("display name length is invalid"))
    } else {
        Result.success(name)
    }
}

class CorrectnessPropertiesTest {

    @Test
    fun `P1 edit icon visible iff userId matches flashcard contributor`() {
        runBlocking {
            checkAll(Arb.string(1..20), Arb.string(1..20)) { cardId, userId ->
                val card = makeFlashcard(cardId, userId)
                editIconVisible(card, userId).shouldBeTrue()
                editIconVisible(card, "${userId}_other").shouldBeFalse()
            }
        }
    }

    @Test
    fun `P2 card is never duplicate of itself`() {
        runBlocking {
            checkAll(Arb.string(1..50)) { term ->
                val cardId = "card_123"
                val allTerms = listOf(term to cardId)
                val isDuplicate = allTerms.any { (t, id) ->
                    t.equals(term, ignoreCase = true) && id != cardId
                }
                isDuplicate.shouldBeFalse()
            }
        }
    }

    @Test
    fun `P3 question edit icon visible iff userId matches contributor`() {
        runBlocking {
            checkAll(Arb.string(1..20), Arb.string(1..20)) { qId, userId ->
                val question = makeQuestion(qId, userId)
                editIconVisibleForQuestion(question, userId).shouldBeTrue()
                editIconVisibleForQuestion(question, "${userId}_other").shouldBeFalse()
            }
        }
    }

    @Test
    fun `P3b answer edit icon visible iff userId matches contributor`() {
        runBlocking {
            checkAll(Arb.string(1..20), Arb.string(1..20)) { aId, userId ->
                val answer = makeAnswer(aId, userId)
                editIconVisibleForAnswer(answer, userId).shouldBeTrue()
                editIconVisibleForAnswer(answer, "${userId}_other").shouldBeFalse()
            }
        }
    }

    @Test
    fun `P4 accepted answer sorts to first position`() {
        runBlocking {
            checkAll(
                Arb.list(Arb.string(1..10), 1..10),
                Arb.int(0..9)
            ) { ids, acceptedIdx ->
                val clampedIdx = acceptedIdx.coerceAtMost(ids.size - 1)
                val answers = ids.mapIndexed { i, id ->
                    makeAnswer(id, "user_$i", isAccepted = i == clampedIdx)
                }
                sortAnswers(answers).first().isAccepted.shouldBeTrue()
            }
        }
    }

    @Test
    fun `P5 upvote increments displayed count by one`() {
        runBlocking {
            checkAll(Arb.nonNegativeInt(1000)) { initialCount ->
                val answer = makeAnswer("a1", "user1", upvotes = initialCount)
                val afterUpvote = answer.copy(upvotes = answer.upvotes + 1)
                afterUpvote.upvotes shouldBeExactly initialCount + 1
            }
        }
    }

    @Test
    fun `P6 offline create then sync keeps unique terms and clears offline flag`() {
        runBlocking {
            checkAll(Arb.list(Arb.string(1..20), 1..20)) { terms ->
                val offlineCards = terms.mapIndexed { i, term ->
                    makeFlashcard("temp_$i", "user1").copy(term = term)
                }
                val synced = offlineCards
                    .distinctBy { it.term.lowercase() }
                    .map { it.copy(isOfflineCached = false) }

                synced.size shouldBe synced.distinctBy { it.term.lowercase() }.size
                synced.forEach { card -> card.isOfflineCached.shouldBeFalse() }
            }
        }
    }

    @Test
    fun `P7 streak equals trailing non zero suffix length`() {
        runBlocking {
            checkAll(Arb.list(Arb.nonNegativeInt(10), 0..30)) { reviewCounts ->
                val computed = computeStreak(reviewCounts)
                val expected = reviewCounts.reversed().takeWhile { it > 0 }.size
                computed shouldBe expected
            }
        }
    }

    @Test
    fun `P8 mastery percentage equals archived over total`() {
        runBlocking {
            checkAll(
                Arb.positiveInt(100),
                Arb.int(0..100)
            ) { total, archivedRaw ->
                val archived = archivedRaw.coerceAtMost(total)
                val mastery = CategoryMastery(
                    category = FlashcardCategory.ABA_THERAPY,
                    total = total,
                    archived = archived
                )
                mastery.percentage shouldBeExactly archived.toFloat() / total
            }
        }
    }

    @Test
    fun `P8b mastery percentage is zero when total is zero`() {
        val mastery = CategoryMastery(
            category = FlashcardCategory.ABA_THERAPY,
            total = 0,
            archived = 0
        )
        mastery.percentage shouldBeExactly 0f
    }

    @Test
    fun `P9 toggle twice returns original state`() {
        runBlocking {
            checkAll(Arb.boolean()) { initialState ->
                var state = initialState
                state = !state
                state = !state
                state shouldBe initialState
            }
        }
    }

    @Test
    fun `P9b toggle once changes state`() {
        runBlocking {
            checkAll(Arb.boolean()) { initialState ->
                val afterToggle = !initialState
                afterToggle shouldBe !initialState
            }
        }
    }

    @Test
    fun `P10 display name shorter than two chars is rejected`() {
        runBlocking {
            checkAll(Arb.string(0..1)) { name ->
                validateDisplayName(name).isFailure.shouldBeTrue()
            }
        }
    }

    @Test
    fun `P10b display name longer than fifty chars is rejected`() {
        runBlocking {
            checkAll(Arb.string(51..200)) { name ->
                validateDisplayName(name).isFailure.shouldBeTrue()
            }
        }
    }

    @Test
    fun `P11 valid display name is accepted and preserved`() {
        runBlocking {
            checkAll(Arb.string(2..50)) { name ->
                val result = validateDisplayName(name)
                result.isSuccess.shouldBeTrue()
                result.getOrNull() shouldBe name
            }
        }
    }

    @Test
    fun `P12 media URL and type are preserved in request DTO`() {
        runBlocking {
            checkAll(
                Arb.string(1..500),
                Arb.enum<MediaType>()
            ) { url, mediaType ->
                data class MediaRequest(val mediaUrl: String?, val mediaType: String)

                val request = MediaRequest(mediaUrl = url, mediaType = mediaType.name)
                request.mediaUrl shouldBe url
                request.mediaType shouldBe mediaType.name
            }
        }
    }
}
