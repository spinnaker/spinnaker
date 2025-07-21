package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit

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
    clientProvider: OkHttpClientProvider
  ): Front50Service =
    Retrofit.Builder()
      .addConverterFactory(InstrumentedJacksonConverter.Factory("Front50", objectMapper))
      .baseUrl(RetrofitUtils.getBaseUrl(front50Endpoint.toString()))
      .client(clientProvider.getClient(DefaultServiceEndpoint("front50", front50Endpoint.toString())))
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .build()
      .create(Front50Service::class.java)
}
