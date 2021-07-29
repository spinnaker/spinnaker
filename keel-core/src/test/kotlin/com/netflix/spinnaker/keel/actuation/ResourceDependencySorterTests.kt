package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.GENERIC_RESOURCE
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec.TargetGroup
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_2
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.EC2_SECURITY_GROUP_V1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.test.dependentResource
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure

class ResourceDependencySorterTests {
  private val metadata = mapOf("application" to "fnord")

  private val locations = SubnetAwareLocations(
    account = "test",
    vpc = "vpc0",
    subnet = "internal (vpc0)",
    regions = setOf(
      SubnetAwareRegionSpec(
        name = "us-east-1",
        availabilityZones = setOf("us-east-1c", "us-east-1d", "us-east-1e")
      )
    )
  )

  private val secGroupSpec1 = SecurityGroupSpec(
    moniker = Moniker("fnord", "test", "secgroup1"),
    description = "secgroup",
    locations = SimpleLocations("test", "vpc0", setOf(SimpleRegionSpec("us-east-1"))),
  )
  
  private val secGroupSpec2 = secGroupSpec1.copy(moniker = Moniker("fnord", "test", "secgroup2"))

  private val targetGroup = TargetGroup("fnord-internal-tg1", port = 8080)

  // ALB 1 depends on security group 1
  private val albSpec1 = ApplicationLoadBalancerSpec(
    moniker = Moniker("fnord", "test", "alb1"),
    locations = locations,
    listeners = emptySet(),
    targetGroups = setOf(targetGroup),
    dependencies = LoadBalancerDependencies(
      securityGroupNames = setOf(secGroupSpec1.moniker.toName())
    )
  )

  // ALB 2 depends on security group 2
  private val albSpec2 = ApplicationLoadBalancerSpec(
    moniker = Moniker("fnord", "test", "alb2"),
    locations = locations,
    listeners = emptySet(),
    targetGroups = emptySet(),
    dependencies = LoadBalancerDependencies(
      securityGroupNames = setOf(secGroupSpec2.moniker.toName())
    )
  )

  // Cluster 1 depends on security group 1 and ALB 1
  private val clusterSpec1 = ClusterSpec(
    moniker = Moniker("fnord", "test", "cluster1"),
    artifactReference = "fnord-deb",
    locations = locations,
    _defaults = ServerGroupSpec(
      launchConfiguration = LaunchConfigurationSpec(
        instanceType = "m5.large",
        ebsOptimized = true,
        iamRole = "fnordInstanceProfile",
        instanceMonitoring = false
      ),
      dependencies = ClusterDependencies(
        loadBalancerNames = setOf(albSpec1.moniker.toName()),
        securityGroupNames = setOf(secGroupSpec1.moniker.toName(), "secgroup-outside-the-environment")
      ),
    )
  )

  // Cluster 2 depends on ALB 1 via a target group
  private val clusterSpec2 = clusterSpec1.copy(
    moniker = Moniker("fnord", "test", "cluster2"),
    _defaults = clusterSpec1.defaults.copy(
      dependencies = ClusterDependencies(
        targetGroups = setOf(targetGroup.name)
      )
    )
  )

  private val environment = Environment(
    name = "test",
    resources = setOf(
      Resource(
        kind = EC2_SECURITY_GROUP_V1.kind,
        metadata = metadata + mapOf("id" to secGroupSpec1.moniker.toName()),
        spec = secGroupSpec1
      ),
      Resource(
        kind = EC2_SECURITY_GROUP_V1.kind,
        metadata = metadata + mapOf("id" to secGroupSpec2.moniker.toName()),
        spec = secGroupSpec2
      ),
      Resource(
        kind = EC2_CLUSTER_V1_1.kind,
        metadata = metadata + mapOf("id" to clusterSpec1.moniker.toName()),
        spec = clusterSpec1
      ),
      Resource(
        kind = EC2_CLUSTER_V1_1.kind,
        metadata = metadata + mapOf("id" to clusterSpec2.moniker.toName()),
        spec = clusterSpec2
      ),
      Resource(
        kind = EC2_APPLICATION_LOAD_BALANCER_V1_2.kind,
        metadata = metadata + mapOf("id" to albSpec1.moniker.toName()),
        spec = albSpec1
      ),
      Resource(
        kind = EC2_APPLICATION_LOAD_BALANCER_V1_2.kind,
        metadata = metadata + mapOf("id" to albSpec2.moniker.toName()),
        spec = albSpec2
      )
    )
  )

  @Test
  fun `correctly sorts resources based on dependencies`() {
    val sorter = ResourceDependencySorter(environment)
    val expectedOrder = listOf(
      EC2_CLUSTER_V1_1,
      EC2_CLUSTER_V1_1,
      EC2_APPLICATION_LOAD_BALANCER_V1_2,
      EC2_APPLICATION_LOAD_BALANCER_V1_2,
      EC2_SECURITY_GROUP_V1,
      EC2_SECURITY_GROUP_V1
    ).map { it.kind }
    expectThat(sorter.sort().map { println(it.name) ; it.kind })
      .isEqualTo(expectedOrder)
  }

  @Test
  fun `throws an exception on cyclic dependencies`() {
    var resource1 = dependentResource()
    val resource2 = dependentResource(
      dependsOn = setOf(Dependency(GENERIC_RESOURCE,"us-east-1", resource1.name, resource1.kind))
    )
    resource1 = resource1.copy(
      spec = resource1.spec.copy(
        dependsOn = setOf(Dependency(GENERIC_RESOURCE,"us-east-1", resource2.name, resource2.kind))
      )
    )
    val environment = Environment(
      name = "test",
      resources = setOf(resource1, resource2)
    )
    val sorter = ResourceDependencySorter(environment)
    expectCatching { sorter.sort() }
      .isFailure()
      .isA<SystemException>()
  }
}
