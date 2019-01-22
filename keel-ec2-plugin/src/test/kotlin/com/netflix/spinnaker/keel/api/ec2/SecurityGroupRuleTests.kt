package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import com.oneeyedmen.minutest.Context
import com.oneeyedmen.minutest.junit.JUnit5Minutests
import com.oneeyedmen.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.propertiesAreEqualTo

internal object SecurityGroupRuleTests : JUnit5Minutests {

  data class Fixture(
    val mapper: ObjectMapper = YAMLMapper().registerKotlinModule(),
    val yaml: String,
    val model: SecurityGroupRule
  )

  override val tests = rootContext<Fixture> {
    context("a self referencing ingress rule") {
      fixture {
        Fixture(
          yaml = """
            |--- !<Reference>
            |protocol: "TCP"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |""".trimMargin(),
          model = ReferenceSecurityGroupRule(
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
          yaml = """
            |--- !<Reference>
            |protocol: "TCP"
            |name: "fnord"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |""".trimMargin(),
          model = ReferenceSecurityGroupRule(
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
          yaml = """
            |--- !<Reference>
            |protocol: "TCP"
            |name: "fnord"
            |account: "prod"
            |vpcName: "vpc0"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |""".trimMargin(),
          model = ReferenceSecurityGroupRule(
            protocol = TCP,
            account = "prod",
            vpcName = "vpc0",
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
          yaml = """
            |--- !<Cidr>
            |protocol: "TCP"
            |portRange:
            |  startPort: 8080
            |  endPort: 8080
            |blockRange: "172.16.0.0/24"
            |""".trimMargin(),
          model = CidrSecurityGroupRule(
            protocol = TCP,
            blockRange = "172.16.0.0/24",
            portRange = PortRange(8080, 8080)
          )
        )
      }

      canSerialize()
      canDeserialize()
    }
  }

  private fun Context<Fixture, Fixture>.canDeserialize() {
    test("deserializes") {
      val deserialized = mapper.readValue<SecurityGroupRule>(yaml)
      expectThat(deserialized).propertiesAreEqualTo(model)
    }
  }

  private fun Context<Fixture, Fixture>.canSerialize() {
    test("serializes") {
      val serialized = mapper.writeValueAsString(model)
      expectThat(serialized).isEqualTo(yaml)
    }
  }
}
