package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldNotMatch
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.configureObjectMapper
import com.netflix.spinnaker.hamkrest.shouldEqual
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
import java.net.URL

abstract class BaseModelParsingTest<out T> {

  private val mapper = configureObjectMapper(
    ObjectMapper(),
    KeelProperties(),
    listOf()
  )
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

    response shouldNotMatch equalTo<T?>(null)
    response shouldEqual expected
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
