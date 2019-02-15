package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object ClusterTests : JUnit5Minutests {
  data class Fixture(
    val mapper: ObjectMapper = YAMLMapper().registerKotlinModule(),
    val yaml: String
  )

  fun tests() = rootContext<Fixture> {
    context("a cluster definition in yaml") {
      fixture {
        Fixture(
          yaml = """
            |---
            |application: fletch_test
            |name: fletch_test-keel-k8s
            |imageId: ami-01fdaa2821a7ea01e
            |accountName: test
            |region: eu-west-1
            |availabilityZones:
            |- eu-west-1a
            |- eu-west-1b
            |- eu-west-1c
            |subnet: internal (vpc0)
            |capacity:
            |  min: 1
            |  max: 1
            |  desired: 1
            |instanceType: m5.large
            |ebsOptimized: true
            |loadBalancerNames: []
            |securityGroupNames:
            |- fletch_test
            |- nf_infrastructure
            |- nf_datacenter
            |instanceMonitoring: false
            |enabledMetrics: []
            |iamRole: fletch_testInstanceProfile
            |keyPair: nf-test-keypair-a
          """.trimMargin()
        )
      }

      test("can be deserialized to a cluster object") {
        val deserialized = mapper.readValue<Cluster>(yaml)
        expectThat(deserialized)
          .get { application }.isEqualTo("fletch_test")
      }
    }
  }
}
