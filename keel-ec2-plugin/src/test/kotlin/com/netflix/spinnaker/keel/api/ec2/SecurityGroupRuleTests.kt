package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.ALL
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol.TCP
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.propertiesAreEqualTo

@SpringBootTest(
  classes = [KeelApplication::class],
  properties = [
    "spring.liquibase.enabled = false" // TODO: ignored by kork's SpringLiquibaseProxy
  ],
  webEnvironment = NONE
)
internal class SecurityGroupRuleTests : JUnit5Minutests {
  @Autowired
  lateinit var mapper: YAMLMapper

  data class Fixture(
    val yaml: String,
    val rule: SecurityGroupRule
  ) {
    val model: SecurityGroupSpec = SecurityGroupSpec(
      moniker = Moniker(
        app = "fnord",
        stack = "ext"
      ),
      locations = SimpleLocations(
        account = "test",
        vpc = "vpc0",
        regions = setOf(SimpleRegionSpec("ap-south-1"))
      ),
      description = "fnord security group",
      inboundRules = setOf(rule)
    )
  }

  fun tests() = rootContext<Fixture> {
    context("a self referencing ingress rule") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |moniker:
            |  app: "fnord"
            |  stack: "ext"
            |locations:
            |  account: "test"
            |  vpc: "vpc0"
            |  regions:
            |  - name: "ap-south-1"
            |description: "fnord security group"
            |inboundRules:
            |- protocol: "TCP"
            |  portRange:
            |    startPort: 8080
            |    endPort: 8080
            |""".trimMargin(),
          rule = ReferenceRule(
            name = "fnord-ext",
            protocol = TCP,
            portRange = PortRange(8080, 8080)
          )
        )
      }

      canDeserialize()

      // when serialized the name is added to the reference rule
      test("serializes") {
        val serialized = mapper.writeValueAsString(model)
        expectThat(serialized).isEqualTo(
            """
            |---
            |moniker:
            |  app: "fnord"
            |  stack: "ext"
            |locations:
            |  account: "test"
            |  vpc: "vpc0"
            |  regions:
            |  - name: "ap-south-1"
            |description: "fnord security group"
            |inboundRules:
            |- protocol: "TCP"
            |  name: "fnord-ext"
            |  portRange:
            |    startPort: 8080
            |    endPort: 8080
            |""".trimMargin())
      }
    }

    context("an ingress rule referencing another security group") {
      fixture {
        Fixture(
          yaml =
            """
            |---
            |moniker:
            |  app: "fnord"
            |  stack: "ext"
            |locations:
            |  account: "test"
            |  vpc: "vpc0"
            |  regions:
            |  - name: "ap-south-1"
            |description: "fnord security group"
            |inboundRules:
            |- protocol: "TCP"
            |  name: "fnord-int"
            |  portRange:
            |    startPort: 8080
            |    endPort: 8080
            |""".trimMargin(),
          rule = ReferenceRule(
            protocol = TCP,
            name = "fnord-int",
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
            |moniker:
            |  app: "fnord"
            |  stack: "ext"
            |locations:
            |  account: "test"
            |  vpc: "vpc0"
            |  regions:
            |  - name: "ap-south-1"
            |description: "fnord security group"
            |inboundRules:
            |- protocol: "TCP"
            |  name: "fnord-ext"
            |  account: "prod"
            |  vpc: "vpc0"
            |  portRange:
            |    startPort: 8080
            |    endPort: 8080
            |""".trimMargin(),
          rule = CrossAccountReferenceRule(
            protocol = TCP,
            account = "prod",
            vpc = "vpc0",
            name = "fnord-ext",
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
            |moniker:
            |  app: "fnord"
            |  stack: "ext"
            |locations:
            |  account: "test"
            |  vpc: "vpc0"
            |  regions:
            |  - name: "ap-south-1"
            |description: "fnord security group"
            |inboundRules:
            |- protocol: "TCP"
            |  portRange:
            |    startPort: 8080
            |    endPort: 8080
            |  blockRange: "172.16.0.0/24"
            |""".trimMargin(),
          rule = CidrRule(
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
            |moniker:
            |  app: "fnord"
            |  stack: "ext"
            |locations:
            |  account: "test"
            |  vpc: "vpc0"
            |  regions:
            |  - name: "ap-south-1"
            |description: "fnord security group"
            |inboundRules:
            |- protocol: "ALL"
            |  portRange: "ALL"
            |  blockRange: "172.16.0.0/24"
            |""".trimMargin(),
          rule = CidrRule(
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
      val deserialized = mapper.readValue<SecurityGroupSpec>(yaml)
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
