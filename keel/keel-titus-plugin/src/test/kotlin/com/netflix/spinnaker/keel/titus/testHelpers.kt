package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_2
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.test.deliveryConfig

fun deliveryConfigWithClusterAndLoadBalancer() =
  deliveryConfig(
    artifact = DockerArtifact(
      name = "fnord",
      branch = "main"
    ),
    env = Environment(
      name = "test",
      resources = setOf(
        Resource(
          kind = TITUS_CLUSTER_V1.kind,
          metadata = mapOf("id" to "fnord-test-cluster", "application" to "fnord"),
          spec = TitusClusterSpec(
            moniker = Moniker("fnord", "test", "cluster"),
            locations = SimpleLocations(
              account = "test",
              regions = setOf(SimpleRegionSpec("us-east-1"))
            ),
            container = ReferenceProvider("fnord"),
            _defaults = TitusServerGroupSpec(
              dependencies = ClusterDependencies(
                loadBalancerNames = setOf("fnord-test-alb")
              )
            )
          )
        ),
        Resource(
          kind = EC2_APPLICATION_LOAD_BALANCER_V1_2.kind,
          metadata = mapOf("id" to "fnord-test-alb", "application" to "fnord"),
          spec = ApplicationLoadBalancerSpec(
            moniker = Moniker("fnord", "test", "alb"),
            locations = SubnetAwareLocations(
              account = "test",
              subnet = "internal",
              regions = setOf(SubnetAwareRegionSpec("us-east-1"))
            ),
            listeners = emptySet(),
            targetGroups = emptySet()
          )
        )
      )
    )
  )
