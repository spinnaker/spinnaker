package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.keel.echo.EchoService
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit

@Configuration
class EchoConfiguration {
  @Autowired
  @Qualifier("retrofit2")
  lateinit var interceptors: List<Interceptor>

  @Bean
  fun echoEndpoint(@Value("\${echo.base-url}") echoBaseUrl: String): HttpUrl =
    echoBaseUrl.toHttpUrlOrNull()
      ?: throw BeanCreationException("Invalid URL: $echoBaseUrl")

  @Bean
  fun echoService(
    echoEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider
  ): EchoService =
    Retrofit.Builder()
      .addConverterFactory(InstrumentedJacksonConverter.Factory("Echo", objectMapper))
      .baseUrl(echoEndpoint)
      .client(
        clientProvider
          .getClient(DefaultServiceEndpoint("echo", echoEndpoint.toString()))
          .newBuilder().apply {
            interceptors.forEach { addInterceptor(it) }
          }.build())
      .build()
      .create(EchoService::class.java)
}
