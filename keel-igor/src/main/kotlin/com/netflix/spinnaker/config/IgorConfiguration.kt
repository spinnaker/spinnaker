package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.igor.ScmService
import com.netflix.spinnaker.keel.services.DeliveryConfigImporter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
  fun artifactService(
    igorEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider
  ): ArtifactService = buildService(objectMapper, igorEndpoint, clientProvider)

  @Bean
  fun scmService(
    igorEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider
  ): ScmService = buildService(objectMapper, igorEndpoint, clientProvider)

  @Bean
  fun deliveryConfigImporter(
    objectMapper: ObjectMapper,
    scmService: ScmService
  ) = DeliveryConfigImporter(objectMapper, scmService)

  private inline fun <reified T> buildService(
    objectMapper: ObjectMapper,
    igorEndpoint: HttpUrl,
    clientProvider: OkHttpClientProvider
  ): T = Retrofit.Builder()
    .addConverterFactory(JacksonConverterFactory.create(objectMapper))
    .baseUrl(igorEndpoint)
    .client(clientProvider.getClient(DefaultServiceEndpoint("igor", igorEndpoint.toString())))
    .build()
    .create(T::class.java)
}
