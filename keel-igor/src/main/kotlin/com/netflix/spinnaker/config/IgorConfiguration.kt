package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ArtifactService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
class IgorConfiguration {
  @Bean
  fun igorEndpoint(@Value("\${igor.base-url}") igorBaseUrl: String): HttpUrl =
    igorBaseUrl.toHttpUrlOrNull()
      ?: throw BeanCreationException("Invalid URL: $igorBaseUrl")

  @Bean
  fun igorService(
    igorEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    retrofitClient: OkHttpClient
  ): ArtifactService =
    Retrofit.Builder()
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .baseUrl(igorEndpoint)
      .client(retrofitClient)
      .build()
      .create(ArtifactService::class.java)
}
