package com.netflix.spinnaker.keel.preview

import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.LOAD_BALANCER
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.DependencyType.TARGET_GROUP
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class DependencyReconciliationTests {
  private val locations = SubnetAwareLocations(
    account = "test",
    vpc = "vpc0",
    subnet = "internal (vpc0)",
    regions = setOf(
      SubnetAwareRegionSpec(
        name = "us-east-1",
        availabilityZones = setOf("us-east-1c", "us-east-1d", "us-east-1e")
      ),
      SubnetAwareRegionSpec(
        name = "us-west-2",
        availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c")
      )
    )
  )

  private val ec2ClusterSpec: ClusterSpec = ClusterSpec(
    moniker = Moniker(
      app = "fnord",
      stack = "test"
    ),
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
        loadBalancerNames = setOf("fnord-internal"),
        securityGroupNames = setOf("fnord", "fnord-elb")
      ),
    ),
    overrides = mapOf(
      "us-east-1" to ServerGroupSpec(
        dependencies = ClusterDependencies(
          loadBalancerNames = setOf("fnord-external"),
          securityGroupNames = setOf("fnord-ext")
        ),
      ),
    )
  )

  private val titusClusterSpec = TitusClusterSpec(
    moniker = Moniker(
      app = "fnord",
      stack = "test"
    ),
    locations = SimpleLocations(
      account = "account",
      regions = setOf(
        SimpleRegionSpec("us-east-1"),
        SimpleRegionSpec("us-west-2")
      )
    ),
    container = ReferenceProvider(reference = "fnord"),
    _defaults = TitusServerGroupSpec(
      dependencies = ClusterDependencies(
        loadBalancerNames = setOf("fnord-internal"),
        securityGroupNames = setOf("fnord", "fnord-elb")
      )
    ),
    overrides = mapOf(
      // additions for us-east-1
      "us-east-1" to TitusServerGroupSpec(
        dependencies = ClusterDependencies(
          loadBalancerNames = setOf("fnord-external"),
          securityGroupNames = setOf("fnord-ext")
        ),
      )
    )
  )

  private val albSpec = ApplicationLoadBalancerSpec(
    moniker = Moniker(
      app = "fnord",
      stack = "test"
    ),
    locations = locations,
    listeners = emptySet(),
    targetGroups = emptySet(),
    dependencies = LoadBalancerDependencies(
      securityGroupNames = setOf("fnord", "fnord-elb")
    )
  )

  private val clbSpec = ClassicLoadBalancerSpec(
    moniker = Moniker(
      app = "fnord",
      stack = "test"
    ),
    locations = locations,
    listeners = emptySet(),
    healthCheck = ClassicLoadBalancerHealthCheck(target = "foo"),
    dependencies = LoadBalancerDependencies(
      securityGroupNames = setOf("fnord", "fnord-elb")
    )
  )

  private val clusterDeps = setOf(
    Dependency(LOAD_BALANCER, "us-east-1", "fnord-internal"),
    Dependency(LOAD_BALANCER, "us-west-2", "fnord-internal"),
    Dependency(SECURITY_GROUP, "us-east-1","fnord"),
    Dependency(SECURITY_GROUP, "us-west-2","fnord"),
    Dependency(SECURITY_GROUP, "us-east-1","fnord-elb"),
    Dependency(SECURITY_GROUP, "us-west-2","fnord-elb"),
    Dependency(LOAD_BALANCER, "us-east-1", "fnord-external"),
    Dependency(SECURITY_GROUP, "us-east-1","fnord-ext")
  )

  private val updatedClusterDeps = setOf("us-east-1", "us-west-2").flatMap { region ->
    setOf(
      Dependency(LOAD_BALANCER, region, "fnord-internal"),
      Dependency(SECURITY_GROUP, region,"fnord"),
      Dependency(TARGET_GROUP, region,"fnord-internal")
    )
  }.toSet() +
    setOf(
      Dependency(LOAD_BALANCER, "us-east-1", "fnord-another"),
      Dependency(SECURITY_GROUP, "us-east-1", "fnord-another"),
      Dependency(TARGET_GROUP, "us-east-1","fnord-another")
    )

  private val loadBalancerDeps = setOf(
    Dependency(SECURITY_GROUP, "us-east-1","fnord"),
    Dependency(SECURITY_GROUP, "us-west-2","fnord"),
    Dependency(SECURITY_GROUP, "us-east-1","fnord-elb"),
    Dependency(SECURITY_GROUP, "us-west-2","fnord-elb"),
  )

  private val updatedLoadBalancerDeps = setOf(
    Dependency(SECURITY_GROUP, "us-east-1","fnord-another")
  )

  @Test
  fun `EC2 cluster spec is forward-compatible with Dependent interface`() {
    expectThat(ec2ClusterSpec.dependsOn)
      .isEqualTo(clusterDeps)
  }

  @Test
  fun `EC2 cluster spec copy with the same dependencies returns the same spec`() {
    expectThat(ec2ClusterSpec.withDependencies(ClusterSpec::class, clusterDeps))
      .isEqualTo(ec2ClusterSpec)
  }

  @Test
  fun `EC2 cluster spec copy with updated dependencies is correct`() {
    expectThat(ec2ClusterSpec.withDependencies(ClusterSpec::class, updatedClusterDeps).dependsOn)
      .isEqualTo(updatedClusterDeps)
  }

  @Test
  fun `Titus cluster spec is forward-compatible with Dependent interface`() {
    expectThat(titusClusterSpec.dependsOn)
      .isEqualTo(clusterDeps)
  }

  @Test
  fun `Titus cluster spec copy with the same dependencies returns the same spec`() {
    expectThat(titusClusterSpec.withDependencies(TitusClusterSpec::class, clusterDeps))
      .isEqualTo(titusClusterSpec)
  }

  @Test
  fun `Titus cluster spec copy with updated dependencies is correct`() {
    expectThat(titusClusterSpec.withDependencies(TitusClusterSpec::class, updatedClusterDeps).dependsOn)
      .isEqualTo(updatedClusterDeps)
  }

  @Test
  fun `ALB spec is forward-compatible with Dependent interface`() {
    expectThat(albSpec.dependsOn)
      .isEqualTo(loadBalancerDeps)
  }

  @Test
  fun `ALB spec copy with the same dependencies returns the same spec`() {
    expectThat(albSpec.withDependencies(ApplicationLoadBalancerSpec::class, loadBalancerDeps))
      .isEqualTo(albSpec)
  }


  @Test
  fun `ALB spec copy with updated dependencies is correct`() {
    expectThat(albSpec.withDependencies(ApplicationLoadBalancerSpec::class, updatedLoadBalancerDeps).dependsOn)
      .isEqualTo(updatedLoadBalancerDeps)
  }

  @Test
  fun `CLB spec is forward-compatible with Dependent interface`() {
    expectThat(clbSpec.dependsOn)
      .isEqualTo(loadBalancerDeps)
  }

  @Test
  fun `CLB spec copy with the same dependencies returns the same spec`() {
    expectThat(clbSpec.withDependencies(ClassicLoadBalancerSpec::class, loadBalancerDeps))
      .isEqualTo(clbSpec)
  }

  @Test
  fun `CLB spec copy with updated dependencies is correct`() {
    expectThat(clbSpec.withDependencies(ClassicLoadBalancerSpec::class, updatedLoadBalancerDeps).dependsOn)
      .isEqualTo(updatedLoadBalancerDeps)
  }
}
