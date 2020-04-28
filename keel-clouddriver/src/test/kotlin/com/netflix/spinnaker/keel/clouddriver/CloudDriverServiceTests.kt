package com.netflix.spinnaker.keel.clouddriver

import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.create
import strikt.api.expectThat
import strikt.assertions.hasSize

class CloudDriverServiceTests : JUnit5Minutests {
  class Fixture {
    val server = MockWebServer()
    val service: CloudDriverService by lazy {
      Retrofit.Builder()
        .addConverterFactory(JacksonConverterFactory.create(configuredObjectMapper()))
        .baseUrl(server.url("/"))
        .build()
        .create<CloudDriverService>()
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      server.shutdown()
    }

    context("getting load balancers for an application") {
      before {
        with(server) {
          enqueue(MockResponse().setBody(javaClass.getResource("/loadBalancers.json").readText()))
          start()
        }
      }

      test("can handle a mixture of application and classic load balancers") {
        val results = runBlocking {
          service.loadBalancersForApplication(DEFAULT_SERVICE_ACCOUNT, "keeldemo")
        }
        expectThat(results).hasSize(10)
      }
    }
  }
}
