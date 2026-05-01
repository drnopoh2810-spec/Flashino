package com.eduspecial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.eduspecial.core.user.DailyGoalQuotaState
import com.eduspecial.core.user.StudyQuotaManager
import com.eduspecial.data.repository.AnalyticsRepository
import com.eduspecial.data.repository.FlashcardRepository
import com.eduspecial.domain.model.Flashcard
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.domain.model.ReviewState
import com.eduspecial.domain.model.SRSResult
import com.eduspecial.domain.usecase.RecordReviewUseCase
import com.eduspecial.presentation.flashcards.StudyViewModel
import com.eduspecial.utils.TtsManager
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: FlashcardRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var recordReviewUseCase: RecordReviewUseCase
    private lateinit var studyQuotaManager: StudyQuotaManager
    private lateinit var ttsManager: TtsManager
    private lateinit var viewModel: StudyViewModel

    private fun makeCard(id: String) = Flashcard(
        id = id,
        term = "Term $id",
        definition = "Definition $id",
        category = FlashcardCategory.ABA_THERAPY,
        contributor = "user1",
        reviewState = ReviewState.REVIEW,
        nextReviewDate = Date(System.currentTimeMillis() - 1000)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        analyticsRepository = mock()
        recordReviewUseCase = mock()
        studyQuotaManager = mock()
        ttsManager = mock()
        whenever(ttsManager.state).thenReturn(MutableStateFlow(TtsManager.TtsState.READY))
        runTest {
            whenever(analyticsRepository.getTodayReviewCount()).thenReturn(0)
            whenever(studyQuotaManager.getDailyGoalQuotaState()).thenReturn(
                DailyGoalQuotaState(
                    selectedGoal = 20,
                    unlockedCap = 20,
                    unlocksUsedToday = 0,
                    canUnlockMore = true
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(cards: List<Flashcard> = emptyList()): StudyViewModel {
        whenever(repository.getStudyQueue()).thenReturn(flowOf(cards))
        return StudyViewModel(
            repository,
            analyticsRepository,
            recordReviewUseCase,
            studyQuotaManager,
            ttsManager
        )
    }

    @Test
    fun `initial state has empty queue and no current card`() = runTest {
        viewModel = createViewModel(emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.studyQueue.isEmpty().shouldBeTrue()
        state.currentCard.shouldBeNull()
        state.isFlipped.shouldBeFalse()
        state.reviewedThisSession shouldBeExactly 0
        state.masteredThisSession shouldBeExactly 0
    }

    @Test
    fun `initial state with cards sets currentCard`() = runTest {
        val cards = listOf(makeCard("1"), makeCard("2"), makeCard("3"))
        viewModel = createViewModel(cards)
        advanceUntilIdle()

        viewModel.uiState.value.currentCard.shouldNotBeNull()
        viewModel.uiState.value.totalCards shouldBeExactly 3
    }

    @Test
    fun `flipCard toggles isFlipped`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1")))
        advanceUntilIdle()

        viewModel.uiState.value.isFlipped.shouldBeFalse()
        viewModel.flipCard()
        viewModel.uiState.value.isFlipped.shouldBeTrue()
        viewModel.flipCard()
        viewModel.uiState.value.isFlipped.shouldBeFalse()
    }

    @Test
    fun `processReview advances to next card`() = runTest {
        val cards = listOf(makeCard("1"), makeCard("2"))
        viewModel = createViewModel(cards)
        advanceUntilIdle()

        val firstCard = viewModel.uiState.value.currentCard
        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()

        val secondCard = viewModel.uiState.value.currentCard
        firstCard.shouldNotBeNull()
        secondCard.shouldNotBeNull()
        secondCard?.id shouldBe cards.map { it.id }.first { it != firstCard.id }
    }

    @Test
    fun `processReview increments reviewedThisSession`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1"), makeCard("2")))
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()
        viewModel.uiState.value.reviewedThisSession shouldBeExactly 1

        viewModel.processReview(SRSResult.Hard)
        advanceUntilIdle()
        viewModel.uiState.value.reviewedThisSession shouldBeExactly 2
    }

    @Test
    fun `processReview with Easy increments masteredThisSession`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1"), makeCard("2")))
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Easy)
        advanceUntilIdle()

        viewModel.uiState.value.masteredThisSession shouldBeExactly 1
    }

    @Test
    fun `processReview with Good does NOT increment masteredThisSession`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1")))
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()

        viewModel.uiState.value.masteredThisSession shouldBeExactly 0
    }

    @Test
    fun `processReview resets isFlipped to false`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1"), makeCard("2")))
        advanceUntilIdle()

        viewModel.flipCard()
        viewModel.uiState.value.isFlipped.shouldBeTrue()
        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()

        viewModel.uiState.value.isFlipped.shouldBeFalse()
    }

    @Test
    fun `processReview on last card sets currentCard to null`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1")))
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()

        viewModel.uiState.value.currentCard.shouldBeNull()
    }

    @Test
    fun `processReview calls repository processReview`() = runTest {
        val card = makeCard("1")
        viewModel = createViewModel(listOf(card))
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()

        verify(repository).processReview(any(), eq(SRSResult.Good))
    }

    @Test
    fun `processReview calls recordReviewUseCase`() = runTest {
        viewModel = createViewModel(listOf(makeCard("1")))
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Easy)
        advanceUntilIdle()

        verify(recordReviewUseCase).invoke(archivedCount = 1)
    }

    @Test
    fun `session complete when all cards reviewed`() = runTest {
        val cards = listOf(makeCard("1"), makeCard("2"))
        viewModel = createViewModel(cards)
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()
        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()

        viewModel.uiState.value.currentCard.shouldBeNull()
        viewModel.uiState.value.reviewedThisSession shouldBeExactly 2
    }

    @Test
    fun `restartSession resets state`() = runTest {
        val cards = listOf(makeCard("1"))
        viewModel = createViewModel(cards)
        advanceUntilIdle()

        viewModel.processReview(SRSResult.Good)
        advanceUntilIdle()
        viewModel.uiState.value.currentCard.shouldBeNull()

        whenever(repository.getStudyQueue()).thenReturn(flowOf(cards))
        viewModel.restartSession()
        advanceUntilIdle()

        viewModel.uiState.value.reviewedThisSession shouldBeExactly 0
        viewModel.uiState.value.masteredThisSession shouldBeExactly 0
        viewModel.uiState.value.isFlipped.shouldBeFalse()
        viewModel.uiState.value.currentCard.shouldNotBeNull()
    }
}
