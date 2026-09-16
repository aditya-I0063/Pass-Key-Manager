package com.bhardwaj.passkey.di

import android.content.Context
import com.bhardwaj.passkey.data.local.VaultDatabaseProvider
import com.bhardwaj.passkey.data.repository.DataStoreRepository
import com.bhardwaj.passkey.data.repository.PasskeyRepository
import com.bhardwaj.passkey.data.security.DefaultKeyDerivation
import com.bhardwaj.passkey.data.security.KeyDerivation
import com.bhardwaj.passkey.domain.repository.PasskeyRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // PassKeyDatabase is deliberately NOT provided here any more. It can only be built once a
    // data encryption key exists, which requires the user to have authenticated, so it is owned
    // by VaultDatabaseProvider and opened on unlock.

    @Provides
    @Singleton
    fun providePasskeyRepository(vault: VaultDatabaseProvider): PasskeyRepository =
        PasskeyRepositoryImpl(vault)

    @Provides
    @Singleton
    fun provideKeyDerivation(impl: DefaultKeyDerivation): KeyDerivation = impl

    @Provides
    @Singleton
    fun provideDataStoreRepository(
        @ApplicationContext context: Context
    ) = DataStoreRepository(context = context)
}
