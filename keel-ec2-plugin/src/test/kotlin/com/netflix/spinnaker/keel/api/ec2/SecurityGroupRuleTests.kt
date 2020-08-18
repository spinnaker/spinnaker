package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.ALL
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.propertiesAreEqualTo

internal class SecurityGroupRuleTests : JUnit5Minutests {

  data class Fixture(
    val mapper: ObjectMapper = configuredYamlMapper().registerKeelEc2ApiModule(),
    val yaml: String,
    val model: SecurityGroupRule
  )

  fun tests() = rootContext<Fixture> {
    context("a self referencing ingress rule") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |protocol: "TCP"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |""".trimMargin(),
          model = ReferenceRule(
            protocol = TCP,
            portRange = PortRange(8080, 8080)
          )
        )
      }

      canSerialize()
      canDeserialize()
    }

    context("an ingress rule referencing another security group") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |protocol: "TCP"
            |name: "fnord"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |""".trimMargin(),
          model = ReferenceRule(
            protocol = TCP,
            name = "fnord",
            portRange = PortRange(8080, 8080)
          )
        )
      }

      canSerialize()
      canDeserialize()
    }

    context("an ingress rule referencing a security group in another account") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |protocol: "TCP"
            |name: "fnord"
            |account: "prod"
            |vpc: "vpc0"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |""".trimMargin(),
          model = CrossAccountReferenceRule(
            protocol = TCP,
            account = "prod",
            vpc = "vpc0",
            name = "fnord",
            portRange = PortRange(8080, 8080)
          )
        )
      }

      canSerialize()
      canDeserialize()
    }

    context("a CIDR rule") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |protocol: "TCP"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |blockRange: "172.16.0.0/24"
            |""".trimMargin(),
          model = CidrRule(
            protocol = TCP,
            blockRange = "172.16.0.0/24",
            portRange = PortRange(8080, 8080)
          )
        )
      }

      canSerialize()
      canDeserialize()
    }
    context("an open CIDR rule") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |protocol: "ALL"
            |portRange: "ALL"
            |blockRange: "172.16.0.0/24"
            |""".trimMargin(),
          model = CidrRule(
            protocol = ALL,
            blockRange = "172.16.0.0/24",
            portRange = AllPorts
          )
        )
      }

      canSerialize()
      canDeserialize()
    }
  }

  private fun TestContextBuilder<Fixture, Fixture>.canDeserialize() {
    test("deserializes") {
      val deserialized = mapper.readValue<SecurityGroupRule>(yaml)
      expectThat(deserialized).propertiesAreEqualTo(model)
    }
  }

  private fun TestContextBuilder<Fixture, Fixture>.canSerialize() {
    test("serializes") {
      val serialized = mapper.writeValueAsString(model)
      expectThat(serialized).isEqualTo(yaml)
    }
  }
}
