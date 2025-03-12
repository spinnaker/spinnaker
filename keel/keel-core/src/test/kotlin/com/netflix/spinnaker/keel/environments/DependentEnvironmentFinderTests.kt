package com.netflix.spinnaker.keel.environments

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.toSimpleLocations
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.events.ResourceState
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.test.resource
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.containsKeys
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.single

internal class DependentEnvironmentFinderTests {

  val locations = SubnetAwareLocations(
    account = "test",
    subnet = "internal",
    regions = setOf(
      SubnetAwareRegionSpec(name = "us-west-2")
    )
  )

  val moniker = Moniker(
    app = "fnord",
    stack = "test"
  )

  val testEnvironment = Environment(
    name = "test",
    resources = setOf(
      resource(
        kind = EC2_CLUSTER_V1_1.kind,
        spec = ClusterSpec(
          moniker = moniker,
          locations = locations
        )
      ),
      resource(
        kind = EC2_SECURITY_GROUP_V1.kind,
        spec = SecurityGroupSpec(
          moniker = moniker,
          locations = locations.toSimpleLocations(),
          description = "a security group"
        )
      )
    )
  )

  val mainEnvironment = Environment(
    name = "main",
    constraints = setOf(
      DependsOnConstraint(
        environment = "test"
      )
    ),
    resources = setOf(
      resource(
        kind = EC2_CLUSTER_V1_1.kind,
        spec = ClusterSpec(
          moniker = moniker.copy(stack = "main"),
          locations = locations.copy(account = "prod")
        )
      ),
      resource(
        kind = EC2_SECURITY_GROUP_V1.kind,
        spec = SecurityGroupSpec(
          moniker = moniker.copy(stack = "main"),
          locations = locations.copy(account = "prod").toSimpleLocations(),
          description = "a security group"
        )
      )
    )
  )

  val disconnectedEnvironment = Environment(
    name = "disconnected",
    resources = setOf(
      resource(
        kind = EC2_CLUSTER_V1_1.kind,
        spec = ClusterSpec(
          moniker = moniker.copy(stack = "disconnected"),
          locations = locations
        )
      ),
      resource(
        kind = EC2_SECURITY_GROUP_V1.kind,
        spec = SecurityGroupSpec(
          moniker = moniker.copy(stack = "disconnected"),
          locations = locations.toSimpleLocations(),
          description = "a security group"
        )
      )
    )
  )

  val deliveryConfig = DeliveryConfig(
    application = "fnord",
    name = "fnord-manifest",
    serviceAccount = "fnord@spinnaker",
    environments = setOf(testEnvironment, mainEnvironment, disconnectedEnvironment)
  )

  val testCluster = testEnvironment.resources.single { it.spec is ClusterSpec }
  val testSecurityGroup = testEnvironment.resources.single { it.spec is SecurityGroupSpec }
  val mainCluster = mainEnvironment.resources.single { it.spec is ClusterSpec }
  val mainSecurityGroup = mainEnvironment.resources.single { it.spec is SecurityGroupSpec }

  val deliveryConfigRepository: DeliveryConfigRepository = mockk()
  val subject = DependentEnvironmentFinder(deliveryConfigRepository)

  @BeforeEach
  fun stubEnvironmentFor() {
    every { deliveryConfigRepository.environmentFor(testCluster.id) } returns testEnvironment
    every { deliveryConfigRepository.environmentFor(testSecurityGroup.id) } returns testEnvironment
    every { deliveryConfigRepository.environmentFor(mainCluster.id) } returns mainEnvironment
    every { deliveryConfigRepository.environmentFor(mainSecurityGroup.id) } returns mainEnvironment
    every { deliveryConfigRepository.deliveryConfigFor(any()) } returns deliveryConfig
  }

  @Test
  fun `resources of same kind is empty for a resource from an environment with no dependencies`() {
    expectThat(subject.resourcesOfSameKindInDependentEnvironments(testCluster))
      .isEmpty()
  }

  @TestFactory
  fun `resources of same kind includes only the correct types of resources`() =
    listOf(
      mainCluster to testCluster,
      mainSecurityGroup to testSecurityGroup
    ).map { (resource, expected) ->
      DynamicTest.dynamicTest("returns only ${resource.spec.javaClass.simpleName} resources") {
        expectThat(subject.resourcesOfSameKindInDependentEnvironments(resource))
          .single() isEqualTo expected
      }
    }

  @Test
  fun `resource statuses is empty for an environment with no dependencies`() {
    expectThat(subject.resourceStatusesInDependentEnvironments(testCluster))
      .isEmpty()
  }

  @Test
  fun `resource statuses includes all resources from a dependent environment`() {
    every {
      deliveryConfigRepository.resourceStatusesInEnvironment(
        deliveryConfig.name,
        testEnvironment.name
      )
    } returns testEnvironment.resources.associate { it.id to ResourceState.Ok }

    expectThat(subject.resourceStatusesInDependentEnvironments(mainCluster))
      .containsKeys(testCluster.id, testSecurityGroup.id)
  }
}
