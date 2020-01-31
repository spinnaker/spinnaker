package com.netflix.spinnaker.keel.integration

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.kork.common.Header.USER
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.junit.jupiter.SpringExtension
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class],
  webEnvironment = MOCK
)
internal class AuthPropagationTests : JUnit5Minutests {

  @Autowired
  lateinit var cloudDriverService: CloudDriverService

  data class Fixture(
    private val cloudDriverService: CloudDriverService
  ) {
    val server = MockWebServer()

    private var _listNetworksResults: Map<String, Set<Network>>? = null
    val listNetworksResults: Map<String, Set<Network>>
      get() = checkNotNull(_listNetworksResults) { "You need to actually make a call first" }

    fun listNetworks() {
      _listNetworksResults = runBlocking {
        cloudDriverService.listNetworks()
      }
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(cloudDriverService)
    }

    before {
      server.start(8080)
    }

    after {
      server.shutdown()
    }

    context("a call to Clouddriver") {
      before {
        server.enqueue(MockResponse().setBody("{}"))

        listNetworks()
      }

      test("calls the endpoint") {
        expect {
          that(server.requestCount).isEqualTo(1)
          that(listNetworksResults).isEmpty()
        }
      }

      test("propagates $USER header") {
        expectThat(server.takeRequest())
          .describedAs("recorded request")
          .getHeader(USER.header)
          .isNotNull()
          .isEqualTo("keel@spinnaker.io")
      }
    }
  }
}

fun Assertion.Builder<RecordedRequest>.getHeader(name: String): Assertion.Builder<String?> =
  get("$name header") {
    getHeader(name)
  }
