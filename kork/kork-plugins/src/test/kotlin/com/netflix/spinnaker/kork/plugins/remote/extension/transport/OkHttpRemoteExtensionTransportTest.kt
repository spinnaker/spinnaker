package com.netflix.spinnaker.kork.plugins.remote.extension.transport

import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.kork.api.plugins.remote.RemoteExtensionConfig
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.netflix.spinnaker.kork.plugins.remote.extension.transport.http.OkHttpRemoteExtensionTransport
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import strikt.api.expectThat
import strikt.assertions.isA

class OkHttpRemoteExtensionTransportTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test ("Read response type") {
      val response = Response.Builder()
        .request(mockk(relaxed = true))
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("Content-Type", "application/json")
        .body(("{\"type\": \"readResponse\", \"foo\": \"bar\"}")
        .toResponseBody((
          "application/json").toMediaType()
        ))
        .build()

      every { client.newCall(any()).execute() } returns response
      val result = subject.read(readQuery)

      expectThat(result).isA<ReadResponse>()
    }

    test ("Write response type") {
      val response = Response.Builder()
        .request(mockk(relaxed = true))
        .protocol(Protocol.HTTP_1_1)
        .code(201)
        .message("OK")
        .header("Content-Type", "application/json")
        .body(("{\"type\": \"writeResponse\", \"foo\": \"bar\"}")
        .toResponseBody((
          "application/json").toMediaType()
        ))
        .build()

      every { client.newCall(any()).execute() } returns response
      val result = subject.write(payload)

      expectThat(result).isA<WriteResponse>()
    }
  }

  private class Fixture {
    val readQuery = Query()
    val payload = Payload()
    val objectMapper: ObjectMapper = jacksonObjectMapper()
    val subTypeLocator = ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
      RemoteExtensionResponse::class.java, listOf("com.netflix.spinnaker.kork.plugins.remote.extension.transport")
    )

    val client: OkHttpClient = mockk(relaxed = true)
    val httpConfig: RemoteExtensionConfig.RemoteExtensionTransportConfig.Http =
      RemoteExtensionConfig.RemoteExtensionTransportConfig.Http(
        "https://example.com",
        mutableMapOf(),
        mutableMapOf(),
        RemoteExtensionConfig.RemoteExtensionTransportConfig.Http.Headers(
          mutableMapOf(), mutableMapOf(), mutableMapOf()
        )
      )

    val subject: OkHttpRemoteExtensionTransport = OkHttpRemoteExtensionTransport(
      objectMapper,
      client,
      httpConfig
    )

    init {
      ObjectMapperSubtypeConfigurer(true).registerSubtypes(objectMapper, listOf(subTypeLocator))
    }
  }

  private class Payload: RemoteExtensionPayload
  private class Query: RemoteExtensionQuery

  @JsonTypeName("writeResponse")
  private class WriteResponse(
    val foo: String
  ): RemoteExtensionResponse

  @JsonTypeName("readResponse")
  data class ReadResponse(
    val foo: String
  ): RemoteExtensionResponse
}
