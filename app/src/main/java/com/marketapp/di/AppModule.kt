package com.marketapp.di

import com.marketapp.BuildConfig
import com.marketapp.data.repository.FakeStoreApi
import com.marketapp.data.repository.ProductRepository
import com.marketapp.data.repository.ProductRepositoryImpl
import com.posthog.PostHogOkHttpInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Using FakeStore API — free, no auth needed, perfect for testing
    private const val BASE_URL = "https://fakestoreapi.com/"

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        // PostHog network telemetry: captures request URL, method, status code, duration,
        // and response size (no request/response bodies). Visible in session replay's
        // Network tab. Only active when PostHog session replay is recording.
        .addInterceptor(PostHogOkHttpInterceptor(captureNetworkTelemetry = true))
        .apply {
            if (BuildConfig.ENABLE_ANALYTICS_LOGGING) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideFakeStoreApi(retrofit: Retrofit): FakeStoreApi =
        retrofit.create(FakeStoreApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
}
