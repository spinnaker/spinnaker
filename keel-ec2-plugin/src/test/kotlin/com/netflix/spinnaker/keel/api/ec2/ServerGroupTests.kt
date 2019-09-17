package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ec2.cluster.ServerGroup
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration

internal object ServerGroupTests : JUnit5Minutests {
  data class Fixture(
    val mapper: ObjectMapper = configuredYamlMapper(),
    val yaml: String
  )

  fun tests() = rootContext<Fixture> {
    context("a server group definition in yaml") {
      fixture {
        Fixture(
          yaml = """
            |---
            |moniker:
            |  app: fletch_test
            |  stack: keel
            |  detail: k8s
            |location:
            |  accountName: test
            |  region: eu-west-1
            |  availabilityZones:
            |  - eu-west-1a
            |  - eu-west-1b
            |  - eu-west-1c
            |  subnet: internal (vpc0)
            |launchConfiguration:
            |  imageId: ami-01fdaa2821a7ea01e
            |  appVersion: mimirdemo-3.18.0-h207.e06f231
            |  instanceType: m5.large
            |  ebsOptimized: true
            |  instanceMonitoring: false
            |  iamRole: fletch_testInstanceProfile
            |  keyPair: nf-test-keypair-a
            |capacity:
            |  min: 1
            |  max: 1
            |  desired: 1
            |dependencies:
            |  loadBalancerNames: []
            |  securityGroupNames:
            |  - fletch_test
            |  - nf_infrastructure
            |  - nf_datacenter
            |health:
            |  enabledMetrics: []
          """.trimMargin()
        )
      }

      derivedContext<ServerGroup>("when deserialized") {
        deriveFixture {
          mapper.readValue(yaml)
        }

        test("can be deserialized to a server group object") {
          expectThat(this)
            .get { moniker.app }.isEqualTo("fletch_test")
        }

        test("populates optional fields") {
          expectThat(this)
            .get { health.cooldown }.isEqualTo(Duration.ofSeconds(10))
        }
      }
    }
  }
}
