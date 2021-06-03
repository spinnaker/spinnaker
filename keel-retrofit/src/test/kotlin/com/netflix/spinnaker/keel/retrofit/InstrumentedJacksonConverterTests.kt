package com.netflix.spinnaker.keel.retrofit

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import strikt.api.expectCatching
import strikt.assertions.cause
import strikt.assertions.first
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNotNull
import strikt.assertions.isSuccess
import strikt.assertions.message

class InstrumentedJacksonConverterTests {

  val server = MockWebServer()

  data class Whatever(val value: String)

  interface Service {
    @GET("/")
    suspend fun whatever(): List<Whatever>
  }

  private val service by lazy {
    Retrofit.Builder()
      .baseUrl(server.url("/"))
      .addConverterFactory(InstrumentedJacksonConverter.Factory("River", ObjectMapper().registerKotlinModule()))
      .client(OkHttpClient.Builder().build())
      .build()
      .create<Service>()
  }

  @BeforeEach
  fun startServer() = server.start()

  @AfterEach
  fun stopServer() = server.shutdown()

  @Test
  fun `valid response`() {
    server.enqueue(MockResponse().setBody("""[{"value":"foo"}]""").addHeader("Content-Type", "application/json"))

    expectCatching { service.whatever() }
      .isSuccess()
      .first()
      .get { value } isEqualTo "foo"
  }

  @Test
  fun `invalid response`() {
    server.enqueue(MockResponse().setBody("""[{}]""").addHeader("Content-Type", "application/json"))

    expectCatching {
      service.whatever()
    }
      .isFailure()
      .isA<UnparseableResponseException>()
      .and {
        cause.isA<JsonMappingException>()
        get { targetSimpleSignature } isEqualTo "List<Whatever>"
      }
  }
}
