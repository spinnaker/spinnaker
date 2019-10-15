package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration

internal object ClassicLoadBalancerSpecTests : JUnit5Minutests {
  data class Fixture(
    val mapper: ObjectMapper = configuredYamlMapper(),
    val yaml: String
  )

  fun tests() = rootContext<Fixture> {
    context("a CLB definition in yaml") {
      fixture {
        Fixture(
          yaml = """
            |---
            |moniker:
            |  app: testapp
            |  stack: managedogge
            |  detail: wow
            |locations:
            |  account: test
            |  vpc: vpc0
            |  subnet: internal (vpc0)
            |  regions:
            |  - name: us-east-1
            |    availabilityZones:
            |    - us-east-1c
            |    - us-east-1d
            |    - us-east-1e
            |healthCheck:
            |  target: HTTP:7001/health
            |listeners:
            | - internalProtocol: HTTP
            |   internalPort: 7001
            |   externalProtocol: HTTP
            |   externalPort: 80
          """.trimMargin()
        )
      }

      derivedContext<ClassicLoadBalancerSpec>("when deserialized") {
        deriveFixture {
          mapper.readValue(yaml)
        }

        test("can be deserialized to a CLB object") {
          expectThat(this)
            .get { listeners.first().internalPort }.isEqualTo(7001)
        }

        test("populates optional fields") {
          expectThat(this)
            .get { healthCheck.interval }.isEqualTo(Duration.ofSeconds(10))
        }
      }
    }
  }
}
