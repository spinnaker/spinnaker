package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.front50.Front50Service
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty("front50.enabled")
class Front50Config {
  @Bean
  fun front50Endpoint(@Value("\${front50.base-url}") front50BaseUrl: String): HttpUrl =
    front50BaseUrl.toHttpUrlOrNull()
      ?: throw BeanCreationException("Invalid URL: $front50BaseUrl")

  @Bean
  fun front50Service(
    front50Endpoint: HttpUrl,
    objectMapper: ObjectMapper,
    retrofitClient: OkHttpClient
  ): Front50Service =
    Retrofit.Builder()
      .addConverterFactory(JacksonConverterFactory.create(objectMapper))
      .baseUrl(front50Endpoint)
      .client(retrofitClient)
      .build()
      .create(Front50Service::class.java)
}
