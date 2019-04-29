package com.netflix.spinnaker.keel.retrofit.model

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.net.URL
import java.nio.charset.Charset

abstract class ModelParsingTestSupport<in S, out E>(serviceType: Class<S>) {

  private val mapper = ObjectMapper()
    .registerModule(KotlinModule())
    .registerModule(JavaTimeModule())
    .enable(INDENT_OUTPUT)
    .disable(FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS)

  private val server = MockWebServer()
  private val service = Retrofit.Builder()
    .baseUrl(server.url("/"))
    .addConverterFactory(JacksonConverterFactory.create(mapper))
    .addCallAdapterFactory(CoroutineCallAdapterFactory())
    .build()
    .create(serviceType)

  abstract val json: URL
  abstract val call: S.() -> Deferred<E?>
  abstract val expected: E

  @Before
  fun startServer() = server.start()

  @After
  fun stopServer() = server.shutdown()

  @Test
  fun `can parse a response into the expected model`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody(json.readText(Charset.forName("UTF-8"))))

    val response = runBlocking {
      service.call().await()
    }

    expectThat(response)
      .isNotNull()
      .isEqualTo(expected)
  }
}
