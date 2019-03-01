package com.netflix.spinnaker.keel.ec2.resource

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.Cluster
import com.netflix.spinnaker.keel.api.ec2.Cluster.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.Cluster.Location
import com.netflix.spinnaker.keel.api.ec2.Cluster.Moniker
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.nhaarman.mockito_kotlin.mock
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.contains
import java.time.Clock

internal object ClusterValidationTests : JUnit5Minutests {

  val objectMapper = configuredObjectMapper()
  val idGenerator = ULID()

  data class Fixture(
    val cluster: Cluster = Cluster(
      moniker = Moniker(
        application = "fnord",
        stack = "test"
      ),
      location = Location(
        accountName = "test",
        region = "ap-south-1",
        subnet = "internal (vpc0)",
        availabilityZones = setOf("ap-south-1a", "ap-south-1b", "ap-south-1c")
      ),
      launchConfiguration = LaunchConfiguration(
        "some-image-id",
        "m4.large",
        true,
        "fnordInstanceProfile",
        "fnordKeyPair"
      )
    )
  ) {
    val resource: Resource<Any> = Resource(
      apiVersion = SPINNAKER_API_V1,
      kind = "cluster",
      metadata = ResourceMetadata(
        name = ResourceName("undefined"),
        uid = idGenerator.nextValue()
      ),
      spec = objectMapper.convertValue<Map<String, Any?>>(cluster)
    )
    val handler: ClusterHandler = ClusterHandler(
      mock(), mock(), mock(), Clock.systemDefaultZone(), objectMapper, idGenerator
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("a cluster spec with no security groups") {
      test("validation assigns default security groups") {
        val validatedResource = handler.validate(resource)

        expectThat(validatedResource.spec.dependencies.securityGroupNames)
          .contains(cluster.moniker.application, "nf-infrastructure", "nf-datacenter")
      }
    }

    context("a cluster spec with some other security groups") {
      deriveFixture {
        copy(cluster = cluster.copy(dependencies = cluster.dependencies.copy(securityGroupNames = setOf("foo", "bar", "baz"))))
      }

      test("validation adds default security groups") {
        val validatedResource = handler.validate(resource)

        expectThat(validatedResource.spec.dependencies.securityGroupNames)
          .contains(cluster.moniker.application, "nf-infrastructure", "nf-datacenter")
          .contains(cluster.dependencies.securityGroupNames)
      }
    }
  }

}
