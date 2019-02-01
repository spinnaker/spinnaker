package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jonpeterson.jackson.module.versioning.VersioningModule
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.jupiter.api.Test
import retrofit.client.Header
import retrofit.client.Request
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.net.URL
import java.nio.charset.Charset

abstract class BaseModelParsingTest<out T> {

  private val mapper = ObjectMapper()
    .registerModule(KotlinModule())
    .registerModule(VersioningModule())
    .registerModule(JavaTimeModule())
    .enable(INDENT_OUTPUT)
    .disable(FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS)

  private val server = MockWebServer()
  private val cloudDriver = Retrofit.Builder()
    .baseUrl(server.url("/"))
    .addConverterFactory(JacksonConverterFactory.create(mapper))
    .addCallAdapterFactory(CoroutineCallAdapterFactory())
    .build()
    .create(CloudDriverService::class.java)

  abstract val json: URL
  abstract val call: CloudDriverService.() -> Deferred<T?>
  abstract val expected: T

  @Before
  fun startServer() = server.start()

  @After
  fun stopServer() = server.shutdown()

  @Test
  fun `can parse a CloudDriver response into the expected model`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody(json.readText(Charset.forName("UTF-8"))))

    val response = runBlocking {
      cloudDriver.call().await()
    }

    expectThat(response)
      .isNotNull()
      .isEqualTo(expected)
  }
}

fun Request.jsonResponse(
  status: Int = 200,
  reason: String = "OK",
  headers: List<Header> = emptyList(),
  body: URL
) =
  Response(
    url,
    status,
    reason,
    headers,
    TypedByteArray("application/json", body.readBytes())
  )
