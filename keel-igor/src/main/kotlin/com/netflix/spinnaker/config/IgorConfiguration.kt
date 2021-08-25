package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.igor.BuildService
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit

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
  fun buildService(
    igorEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider
  ): BuildService = buildService(objectMapper, igorEndpoint, clientProvider)

  @Bean
  fun deliveryConfigImporter(
    scmService: ScmService,
    front50Cache: Front50Cache,
    yamlMapper: YAMLMapper,
  ) = DeliveryConfigImporter(scmService, front50Cache, yamlMapper)

  private inline fun <reified T> buildService(
    objectMapper: ObjectMapper,
    igorEndpoint: HttpUrl,
    clientProvider: OkHttpClientProvider
  ): T = Retrofit.Builder()
    .addConverterFactory(InstrumentedJacksonConverter.Factory("Igor", objectMapper))
    .baseUrl(igorEndpoint)
    .client(clientProvider.getClient(DefaultServiceEndpoint("igor", igorEndpoint.toString())))
    .build()
    .create(T::class.java)
}
