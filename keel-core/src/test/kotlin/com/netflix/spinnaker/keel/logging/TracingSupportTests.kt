package com.netflix.spinnaker.keel.logging

import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.X_SPINNAKER_RESOURCE_ID
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.withTracingContext
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class TracingSupportTests : JUnit5Minutests {
  val resource = resource()
  val exportable = Exportable(
    cloudProvider = "aws",
    account = "test",
    user = "fzlem@netflix.com",
    moniker = Moniker("keel"),
    regions = emptySet(),
    kind = resource.kind
  )

  fun tests() = rootContext {
    before {
      MDC.clear()
    }

    after {
      MDC.clear()
    }

    context("running with tracing context") {
      test("injects X-SPINNAKER-RESOURCE-ID to MDC in the coroutine context from resource") {
        runBlocking {
          launch {
            withTracingContext(resource) {
              expectThat(MDC.get(X_SPINNAKER_RESOURCE_ID))
                .isEqualTo(resource.id.toString())
            }
          }
        }
      }

      test("injects X-SPINNAKER-RESOURCE-ID to MDC in the coroutine context from exportable") {
        runBlocking {
          launch {
            withTracingContext(exportable) {
              expectThat(MDC.get(X_SPINNAKER_RESOURCE_ID))
                .isEqualTo(exportable.toResourceId().toString())
            }
          }
        }
      }

      test("removes X-SPINNAKER-RESOURCE-ID from MDC after block executes") {
        runBlocking {
          MDC.put("foo", "bar")
          launch {
            withTracingContext(resource) {
              expectThat(MDC.get(X_SPINNAKER_RESOURCE_ID))
                .isEqualTo(resource.id.toString())
            }
          }.join()
          expectThat(MDC.get(X_SPINNAKER_RESOURCE_ID))
            .isNull()
          expectThat(MDC.get("foo"))
            .isEqualTo("bar")
        }
      }

      test("does not mix up X-SPINNAKER-RESOURCE-ID between parallel coroutines") {
        runBlocking {
          val coroutine1 = async {
            withTracingContext(resource) {
              println("X-SPINNAKER-RESOURCE-ID: ${MDC.get(X_SPINNAKER_RESOURCE_ID)}")
              expectThat(MDC.get(X_SPINNAKER_RESOURCE_ID))
                .isEqualTo(resource.id.toString())
            }
          }
          val coroutine2 = async {
            val anotherResource = resource()
            withTracingContext(anotherResource) {
              println("X-SPINNAKER-RESOURCE-ID: ${MDC.get(X_SPINNAKER_RESOURCE_ID)}")
              expectThat(MDC.get(X_SPINNAKER_RESOURCE_ID))
                .isEqualTo(anotherResource.id.toString())
            }
          }
          coroutine1.await()
          coroutine2.await()
        }
      }
    }
  }
}
