package com.netflix.spinnaker.keel.preview

import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.LOAD_BALANCER
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.DependencyType.TARGET_GROUP
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.test.applicationLoadBalancer
import com.netflix.spinnaker.keel.test.classicLoadBalancer
import com.netflix.spinnaker.keel.test.ec2Cluster
import com.netflix.spinnaker.keel.test.titusCluster
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class DependencyReconciliationTests {
  private val ec2ClusterSpec = ec2Cluster().spec.copy(
    overrides = mapOf(
      "us-east-1" to ServerGroupSpec(
        dependencies = ClusterDependencies(
          loadBalancerNames = setOf("fnord-external"),
          securityGroupNames = setOf("fnord-ext")
        )
      )
    )
  )

  private val titusClusterSpec = titusCluster().spec.copy(
    overrides = mapOf(
      "us-east-1" to TitusServerGroupSpec(
        dependencies = ClusterDependencies(
          loadBalancerNames = setOf("fnord-external"),
          securityGroupNames = setOf("fnord-ext")
        )
      )
    )
  )

  private val albSpec = applicationLoadBalancer().spec

  private val clbSpec = classicLoadBalancer().spec

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
