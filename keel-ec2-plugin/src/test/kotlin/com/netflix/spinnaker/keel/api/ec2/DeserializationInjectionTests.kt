package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import strikt.api.expectCatching
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isSuccess

@SpringBootTest(
  properties = [
    "keel.plugins.ec2.enabled = true",
    "spring.liquibase.enabled = false" // TODO: ignored by kork's SpringLiquibaseProxy
  ],
  classes = [KeelApplication::class],
  webEnvironment = NONE
)
internal class DeserializationInjectionTests {

  @Autowired
  lateinit var mapper: YAMLMapper

  @Test
  fun `can deserialize a delivery config with injected locations and security group names`() {
    val yaml = """
      |name: fnord-manifest
      |application: fnord
      |artifacts: []
      |environments:
      |- name: test
      |  constraints: []
      |  notifications: []
      |  locations:
      |    account: test
      |    regions:
      |    - name: us-west-2
      |    - name: us-east-1
      |  resources:
      |  - kind: ec2/security-group@v1
      |    spec:
      |      moniker:
      |        app: fnord
      |      description: self-referential ingress rule
      |      inboundRules:
      |      - protocol: TCP
      |        portRange:
      |          startPort: 8080
      |          endPort: 8080
    """.trimMargin()

    expectCatching {
      mapper.readValue<SubmittedDeliveryConfig>(yaml)
    }
      .isSuccess()
      .get {
        environments.first().resources.first().spec
      }
      .isA<SecurityGroupSpec>()
      .and {
        get { locations.account } isEqualTo "test"
      }
      .and {
        get { inboundRules.first() }
          .isA<ReferenceRule>()
          .get { name } isEqualTo "fnord"
      }
  }

  @Test
  fun `can deserialize a security group resource with ingress rules in a region override`() {
    val yaml = """
      |moniker:
      |  app: fnord
      |description: self-referential ingress rule
      |locations:
      |  account: test
      |  regions:
      |  - name: us-west-2
      |  - name: us-east-1
      |inboundRules: []
      |overrides:
      |  us-west-2:
      |    inboundRules:
      |    - protocol: TCP
      |      portRange:
      |        startPort: 8080
      |        endPort: 8080
    """.trimMargin()

    expectCatching {
      mapper.readValue<SecurityGroupSpec>(yaml)
    }
      .isSuccess()
        .and {
          get { inboundRules }.isEmpty()
          get { overrides["us-west-2"]?.inboundRules }.isNotNull().hasSize(1)
        }
  }
}
