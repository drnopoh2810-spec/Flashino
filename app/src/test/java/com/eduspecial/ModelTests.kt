package com.eduspecial

import com.eduspecial.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class FlashcardModelTest {

    @Test
    fun `flashcard default reviewState is NEW`() {
        val card = Flashcard(
            id = "1",
            term = "ABA",
            definition = "Applied Behavior Analysis",
            category = FlashcardCategory.ABA_THERAPY,
            contributor = "user1"
        )
        assertEquals(ReviewState.NEW, card.reviewState)
    }

    @Test
    fun `flashcard default easeFactor is 2_5`() {
        val card = Flashcard(
            id = "1", term = "Test", definition = "Test def",
            category = FlashcardCategory.ABA_THERAPY, contributor = "user1"
        )
        assertEquals(2.5f, card.easeFactor)
    }

    @Test
    fun `flashcard default mediaType is NONE`() {
        val card = Flashcard(
            id = "1", term = "Test", definition = "Test def",
            category = FlashcardCategory.ABA_THERAPY, contributor = "user1"
        )
        assertEquals(MediaType.NONE, card.mediaType)
    }
}

class QAModelTest {

    @Test
    fun `question default upvotes is zero`() {
        val question = QAQuestion(
            id = "q1",
            question = "What is ABA?",
            category = FlashcardCategory.ABA_THERAPY,
            contributor = "user1"
        )
        assertEquals(0, question.upvotes)
    }

    @Test
    fun `question default isAnswered is false`() {
        val question = QAQuestion(
            id = "q1", question = "What is ABA?",
            category = FlashcardCategory.ABA_THERAPY, contributor = "user1"
        )
        assertFalse(question.isAnswered)
    }

    @Test
    fun `answer default isAccepted is false`() {
        val answer = QAAnswer(
            id = "a1", questionId = "q1",
            content = "ABA stands for Applied Behavior Analysis",
            contributor = "user2"
        )
        assertFalse(answer.isAccepted)
    }
}

class SRSResultTest {

    @Test
    fun `SRSResult Easy creates correct type`() {
        val result: SRSResult = SRSResult.Easy
        assertTrue(result is SRSResult.Easy)
    }

    @Test
    fun `SRSResult Again creates correct type`() {
        val result: SRSResult = SRSResult.Again
        assertTrue(result is SRSResult.Again)
    }

    @Test
    fun `all SRS results are distinct types`() {
        val easy: SRSResult  = SRSResult.Easy
        val good: SRSResult  = SRSResult.Good
        val hard: SRSResult  = SRSResult.Hard
        val again: SRSResult = SRSResult.Again
        assertNotEquals(easy, good)
        assertNotEquals(good, hard)
        assertNotEquals(hard, again)
    }
}

class FlashcardCategoryTest {

    @Test
    fun `all 10 categories exist`() {
        assertEquals(10, FlashcardCategory.values().size)
    }

    @Test
    fun `ABA_THERAPY category exists`() {
        assertNotNull(FlashcardCategory.valueOf("ABA_THERAPY"))
    }

    @Test
    fun `AUTISM_SPECTRUM category exists`() {
        assertNotNull(FlashcardCategory.valueOf("AUTISM_SPECTRUM"))
    }
}

class SearchResultTest {

    @Test
    fun `SearchResult has correct type`() {
        val result = SearchResult(
            id = "1",
            type = SearchResultType.FLASHCARD,
            title = "ABA Therapy",
            subtitle = "Applied Behavior Analysis"
        )
        assertEquals(SearchResultType.FLASHCARD, result.type)
    }

    @Test
    fun `SearchResult types are distinct`() {
        assertNotEquals(SearchResultType.FLASHCARD, SearchResultType.QUESTION)
    }
}
