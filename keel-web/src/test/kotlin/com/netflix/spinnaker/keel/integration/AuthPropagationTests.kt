package com.netflix.spinnaker.keel.integration

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.integration.AuthPropagationTests.MockFiat
import com.netflix.spinnaker.kork.common.Header.ACCOUNTS
import com.netflix.spinnaker.kork.common.Header.USER
import com.netflix.spinnaker.kork.common.Header.USER_ORIGIN
import dev.minutest.experimental.SKIP
import dev.minutest.experimental.minus
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@SpringBootTest(
  classes = [KeelApplication::class, MockFiat::class],
  webEnvironment = MOCK
)
internal class AuthPropagationTests
@Autowired constructor(val cloudDriverService: CloudDriverService) : JUnit5Minutests {

  @Configuration
  class MockFiat {
    val mockAccount = Account()
    val mockPermission = UserPermission()

    init {
      mockAccount.cloudProvider = "aws"
      mockAccount.name = "test"
      mockAccount.permissions = Permissions.factory(mapOf(Authorization.READ to listOf("role")))
      mockPermission.accounts = setOf(mockAccount)
    }

    @Bean
    fun fiatPermissionEvaluator() = mockk<FiatPermissionEvaluator>() {
      every {
        getPermission(any())
      } returns mockPermission.view
    }
  }

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

      test("propagates ${USER.header} header") {
        expectThat(server.takeRequest())
          .describedAs("recorded request")
          .getHeader(USER.header)
          .isNotNull()
          .isEqualTo("keel@spinnaker.io")
      }

      // TODO: reenable when issue in SpinnakerHeadersInterceptor is addressed
      SKIP - test("includes ${ACCOUNTS.header} header") {
        expectThat(server.takeRequest())
          .describedAs("recorded request")
          .getHeader(ACCOUNTS.header)
          .isNotNull()
          .isEqualTo("test")
      }

      test("includes ${USER_ORIGIN.header} header") {
        expectThat(server.takeRequest())
          .describedAs("recorded request")
          .getHeader(USER_ORIGIN.header)
          .isNotNull()
          .isEqualTo("keel")
      }
    }
  }
}

fun Assertion.Builder<RecordedRequest>.getHeader(name: String): Assertion.Builder<String?> =
  get("$name header") {
    getHeader(name)
  }
