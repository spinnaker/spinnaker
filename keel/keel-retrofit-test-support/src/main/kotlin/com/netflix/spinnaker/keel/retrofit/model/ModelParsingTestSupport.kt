package com.netflix.spinnaker.keel.retrofit.model

import tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import tools.jackson.databind.cfg.DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS
import tools.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import strikt.api.expectThat
import strikt.assertions.isNotNull
import strikt.java.propertiesAreEqualTo

abstract class ModelParsingTestSupport<in S : Any, out E : Any>(serviceType: Class<S>) {

  private val mapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .enable(INDENT_OUTPUT)
    .disable(FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
    .build()

  private val server = MockWebServer()
  private val service = Retrofit.Builder()
    .baseUrl(server.url("/"))
    .addConverterFactory(InstrumentedJacksonConverter.Factory(serviceType.simpleName, mapper))
    .build()
    .create(serviceType)

  abstract val json: String
  abstract suspend fun S.call(): E?
  abstract val expected: E

  @Before
  fun startServer() = server.start()

  @After
  fun stopServer() = server.shutdown()

  @Test
  fun `can parse a response into the expected model`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody(json))

    val response = runBlocking {
      service.call()
    }

    expectThat(response)
      .isNotNull()
      .propertiesAreEqualTo(expected)
  }
}
