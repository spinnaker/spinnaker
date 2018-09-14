package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jonpeterson.jackson.module.versioning.VersioningModule
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test
import retrofit.Endpoints.newFixedEndpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.client.Header
import retrofit.client.Request
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import retrofit.mime.TypedByteArray
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.net.URL

abstract class BaseModelParsingTest<out T> {

  private val mapper = ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .registerModule(KotlinModule())
    .registerModule(VersioningModule())
    .registerModule(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)

  private val client = mock<Client>()
  private val cloudDriver = RestAdapter.Builder()
    .setEndpoint(newFixedEndpoint("https://spinnaker.💩"))
    .setClient(client)
    .setConverter(JacksonConverter(mapper))
    .build()
    .create(CloudDriverService::class.java)

  abstract val json: URL
  abstract val call: CloudDriverService.() -> T?
  abstract val expected: T

  @Test
  fun `can parse a CloudDriver response into the expected model`() {
    whenever(
      client.execute(any())
    ) doAnswer {
      it.getArgument<Request>(0).jsonResponse(body = json)
    }

    val response = cloudDriver.call()

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
