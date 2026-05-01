package com.eduspecial.di

import android.content.Context
import androidx.room.Room
import com.eduspecial.core.ads.AdManager
import com.eduspecial.data.local.EduSpecialDatabase
import com.eduspecial.data.local.dao.*
import com.eduspecial.data.remote.api.AudioBackendClient
import com.eduspecial.data.remote.api.CloudinaryService
import com.eduspecial.data.repository.*
import com.eduspecial.data.manager.RoleManager
import com.eduspecial.utils.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance("default")

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EduSpecialDatabase =
        Room.databaseBuilder(
            context,
            EduSpecialDatabase::class.java,
            EduSpecialDatabase.DATABASE_NAME
        )
            .addMigrations(EduSpecialDatabase.MIGRATION_1_2)
            .addMigrations(EduSpecialDatabase.MIGRATION_5_6)
            .addMigrations(EduSpecialDatabase.MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFlashcardDao(db: EduSpecialDatabase): FlashcardDao = db.flashcardDao()
    @Provides fun provideQADao(db: EduSpecialDatabase): QADao = db.qaDao()
    @Provides fun providePendingSubmissionDao(db: EduSpecialDatabase): PendingSubmissionDao = db.pendingSubmissionDao()
    @Provides fun provideBookmarkDao(db: EduSpecialDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideAnalyticsDao(db: EduSpecialDatabase): AnalyticsDao = db.analyticsDao()

    @Provides @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor = NetworkMonitor(context)

    @Provides @Singleton
    fun provideCloudinaryService(
        @ApplicationContext context: Context,
        configRepository: ConfigRepository
    ): CloudinaryService = CloudinaryService(context, configRepository).also { it.initialize() }

    @Provides @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext context: Context): UserPreferencesDataStore =
        UserPreferencesDataStore(context)

    @Provides
    @Singleton
    fun provideAdManager(@ApplicationContext context: Context): AdManager =
        AdManager.getInstance(context)

    @Provides @Singleton
    fun provideBookmarkRepository(
        bookmarkDao: BookmarkDao,
        flashcardDao: FlashcardDao,
        qaDao: QADao
    ): BookmarkRepository = BookmarkRepository(bookmarkDao, flashcardDao, qaDao)

    @Provides @Singleton
    fun provideAnalyticsRepository(analyticsDao: AnalyticsDao): AnalyticsRepository =
        AnalyticsRepository(analyticsDao)

    @Provides @Singleton
    fun provideNotificationScheduler(@ApplicationContext context: Context): NotificationScheduler =
        NotificationScheduler(context)

    @Provides @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context,
        networkMonitor: NetworkMonitor,
        flashcardRepository: FlashcardRepository,
        cloudinaryService: CloudinaryService,
        audioBackendClient: AudioBackendClient
    ): TtsManager = TtsManager(
        context,
        networkMonitor,
        flashcardRepository,
        cloudinaryService,
        audioBackendClient
    )

    @Provides @Singleton
    fun provideCircuitBreaker(): CircuitBreaker = CircuitBreaker()

    @Provides @Singleton
    fun provideApiHealthMonitor(
        circuitBreaker: CircuitBreaker,
        networkMonitor: NetworkMonitor
    ): ApiHealthMonitor = ApiHealthMonitor(circuitBreaker, networkMonitor)

    @Provides @Singleton
    fun provideFlashcardPagingRepository(
        flashcardDao: FlashcardDao
    ): FlashcardPagingRepository = FlashcardPagingRepository(flashcardDao)

    @Provides @Singleton
    fun provideLeaderboardRepository(
        qaDao: QADao,
        authRepository: AuthRepository
    ): LeaderboardRepository = LeaderboardRepository(qaDao, authRepository)

    @Provides @Singleton
    fun provideAlgoliaSearchService(
        configRepository: ConfigRepository
    ): com.eduspecial.data.remote.search.AlgoliaSearchService =
        com.eduspecial.data.remote.search.AlgoliaSearchService(configRepository)

    @Provides @Singleton
    fun provideNotificationRepository(
        authRepository: AuthRepository,
        notificationScheduler: NotificationScheduler
    ): NotificationRepository = NotificationRepository(authRepository, notificationScheduler)

    @Provides @Singleton
    fun provideContentModerationService(
    ): com.eduspecial.data.remote.moderation.ContentModerationService =
        com.eduspecial.data.remote.moderation.ContentModerationService()

    @Provides @Singleton
    fun provideModerationRepository(
        authRepository: AuthRepository,
        contentModerationService: com.eduspecial.data.remote.moderation.ContentModerationService
    ): ModerationRepository = ModerationRepository(authRepository, contentModerationService)

    @Provides @Singleton
    fun provideRoleManager(
        firestore: FirebaseFirestore,
        firebaseAuth: FirebaseAuth,
        prefs: UserPreferencesDataStore
    ): RoleManager = RoleManager(firestore, firebaseAuth, prefs)
}
