package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.netflix.spinnaker.keel.mahe.DynamicPropertyService
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty("\${mahe.enabled}")
class MaheConfiguration {
  @Bean
  fun maheEndpoint(@Value("\${mahe.base-url}") maheBaseUrl: String): HttpUrl =
    HttpUrl.parse(maheBaseUrl)
      ?: throw BeanCreationException("Invalid URL: $maheBaseUrl")

  @Bean
  fun maheService(
    maheEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    retrofitClient: OkHttpClient)
    : DynamicPropertyService =
    Retrofit.Builder()
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .addCallAdapterFactory(CoroutineCallAdapterFactory())
      .baseUrl(maheEndpoint)
      .client(retrofitClient)
      .build()
      .create(DynamicPropertyService::class.java)
}
