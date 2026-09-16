package com.bhardwaj.passkey.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.bhardwaj.passkey.BuildConfig
import com.bhardwaj.passkey.data.local.PassKeyDatabase
import com.bhardwaj.passkey.data.repository.DataStoreRepository
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.domain.repository.PasskeyRepositoryImpl
import com.bhardwaj.passkey.utils.Constants
import com.bhardwaj.passkey.utils.MIGRATION_1_2
import com.bhardwaj.passkey.utils.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(appContext: Application): PassKeyDatabase {
        return Room.databaseBuilder(
            context = appContext,
            klass = PassKeyDatabase::class.java,
            name = Constants.PASS_KEY_DATABASE
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // FIXME(5.7.0): this is a single build-time constant, identical for every install
            //  and recoverable from the APK, so the database is encrypted against a filesystem
            //  thief and nothing else. It is replaced in 5.7.0 by a per-install random key
            //  wrapped by an AndroidKeyStore key; this value then survives only to open and
            //  re-key pre-5.7 databases.
            .openHelperFactory(
                factory = SupportOpenHelperFactory(BuildConfig.LEGACY_PASS_PHRASE.toByteArray())
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTodoRepository(db: PassKeyDatabase): PasskeyRepository {
        return PasskeyRepositoryImpl(db, db.previewDao, db.detailsDao)
    }

    @Provides
    @Singleton
    fun provideDataStoreRepository(
        @ApplicationContext context: Context
    ) = DataStoreRepository(context = context)
}