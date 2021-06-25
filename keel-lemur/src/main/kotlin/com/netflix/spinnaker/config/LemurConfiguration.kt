package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.caffeine.CacheLoadingException
import com.netflix.spinnaker.keel.lemur.LemurCertificateResponse
import com.netflix.spinnaker.keel.lemur.LemurService
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.future.await
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.AUTHORIZATION
import retrofit2.Retrofit

@Configuration
@ConditionalOnProperty("lemur.base-url")
@EnableConfigurationProperties(LemurProperties::class)
class LemurConfiguration {
  @Bean
  fun lemurEndpoint(lemurProperties: LemurProperties): HttpUrl =
    lemurProperties.baseUrl?.toHttpUrlOrNull()
      ?: throw BeanCreationException("Invalid URL: ${lemurProperties.baseUrl}")

  @Bean
  fun lemurService(
    objectMapper: ObjectMapper,
    lemurEndpoint: HttpUrl,
    lemurProperties: LemurProperties
  ): LemurService {
    val client = OkHttpClient
      .Builder()
      .addInterceptor { chain ->
        chain.proceed(
          chain
            .request()
            .newBuilder()
            .header(AUTHORIZATION, "Bearer ${lemurProperties.token}")
            .build()
        )
      }
      .build()
    return Retrofit.Builder()
      .addConverterFactory(InstrumentedJacksonConverter.Factory("Lemur", objectMapper))
      .baseUrl(lemurEndpoint)
      .client(client)
      .build()
      .create(LemurService::class.java)
  }

  @Bean
  fun lemurCertificateByName(cacheFactory: CacheFactory, lemurService: LemurService) : suspend (String) -> LemurCertificateResponse {
    val cacheName = "lemurCertificatesByName"
    val cache = cacheFactory
      .asyncLoadingCache<String, LemurCertificateResponse>(cacheName) { name ->
        runCatching {
          lemurService.certificateByName(name)
        }
          .getOrElse { ex ->
            if (ex.isNotFound) {
              null
            } else {
              throw CacheLoadingException(cacheName, name, ex)
            }
          }
      }
    return {
      cache.get(it).await()
    }
  }
}

@ConfigurationProperties(prefix = "lemur")
class LemurProperties {
  var baseUrl: String? = null
  var token: String? = null
}
