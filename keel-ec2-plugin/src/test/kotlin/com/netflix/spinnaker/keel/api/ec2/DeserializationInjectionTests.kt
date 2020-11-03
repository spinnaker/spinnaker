package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.extensions.DefaultExtensionRegistry
import com.netflix.spinnaker.keel.jackson.registerKeelApiModule
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess

internal class DeserializationInjectionTests {

  val mapper = configuredYamlMapper()
    .registerKeelApiModule()
    .registerKeelEc2ApiModule()
  val extensionRegistry = DefaultExtensionRegistry(listOf(mapper)).apply {
    register(
      baseType = ResourceSpec::class.java,
      extensionType = SecurityGroupSpec::class.java,
      discriminator = EC2_SECURITY_GROUP_V1.kind.toString()
    )
  }

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
}
