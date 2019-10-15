package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object ApplicationLoadBalancerSpecTests : JUnit5Minutests {

  data class Fixture(
    val mapper: ObjectMapper = configuredYamlMapper(),
    val yaml: String
  )

  fun tests() = rootContext<Fixture> {
    context("a simple ALB definition in yaml") {
      fixture {
        Fixture(
          yaml = """
            |---
            |moniker:
            |  app: testapp
            |  stack: managedogge
            |  detail: wow
            |locations:
            |  accountName: test
            |  vpcName: vpc0
            |  regions:
            |  - region: us-east-1
            |    subnet: internal (vpc0)
            |    availabilityZones:
            |    - us-east-1c
            |    - us-east-1d
            |    - us-east-1e
            |listeners:
            | - port: 80
            |   protocol: HTTP
            |targetGroups:
            | - name: managedogge-wow-tg
            |   port: 7001
          """.trimMargin()
        )
      }

      derivedContext<ApplicationLoadBalancerSpec>("when deserialized") {
        deriveFixture {
          mapper.readValue(yaml)
        }

        test("can be deserialized to an ALB object") {
          expectThat(this)
            .get { listeners.first().port }.isEqualTo(80)
        }

        test("populates default values for missing fields") {
          expectThat(this)
            .get { targetGroups.first().healthCheckPath }.isEqualTo("/healthcheck")
        }
      }
    }
  }
}
