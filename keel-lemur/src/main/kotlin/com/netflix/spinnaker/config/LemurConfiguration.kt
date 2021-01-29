package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.lemur.LemurService
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
import retrofit2.converter.jackson.JacksonConverterFactory

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
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .baseUrl(lemurEndpoint)
      .client(client)
      .build()
      .create(LemurService::class.java)
  }
}

@ConfigurationProperties(prefix = "lemur")
class LemurProperties {
  var baseUrl: String? = null
  var token: String? = null
}
