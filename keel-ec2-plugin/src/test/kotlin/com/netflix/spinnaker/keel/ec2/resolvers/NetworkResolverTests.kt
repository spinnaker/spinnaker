package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.HealthSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType.ELB
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.ec2.resolvers.NetworkResolver.Companion.DEFAULT_SUBNET_PURPOSE
import com.netflix.spinnaker.keel.ec2.resolvers.NetworkResolver.Companion.DEFAULT_VPC_NAME
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.apache.commons.lang3.RandomStringUtils.randomNumeric
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.time.Duration

internal abstract class NetworkResolverTests<T : Locatable<SubnetAwareRegionSpec>> : JUnit5Minutests {
  protected abstract fun createSubject(): NetworkResolver<T>
  protected abstract fun createResource(vpcName: String?, subnetPurpose: String?): Resource<T>

  data class Fixture<T : Locatable<*>>(
    val subject: NetworkResolver<T>,
    val resource: Resource<T>
  ) {
    val resolved by lazy { subject(resource) }
  }

  fun tests() = rootContext<Fixture<T>> {
    context("neither VPC name or subnet is specified") {
      fixture {
        Fixture(
          createSubject(),
          createResource(null, null)
        )
      }

      test("supports the resource kind") {
        expectThat(listOf(subject).supporting(resource))
          .containsExactly(subject)
      }

      test("VPC name is defaulted") {
        expectThat(resolved.spec.locations.vpcName)
          .isEqualTo(DEFAULT_VPC_NAME)
      }

      test("subnets are defaulted") {
        expectThat(resolved.spec.locations.subnet)
          .isEqualTo(DEFAULT_SUBNET_PURPOSE.format(DEFAULT_VPC_NAME))
      }
    }

    context("VPC name is specified but subnets are not") {
      fixture {
        Fixture(
          createSubject(),
          createResource("vpc5", null)
        )
      }

      test("subnets are defaulted based on VPC name") {
        expectThat(resolved.spec.locations.subnet)
          .isEqualTo(DEFAULT_SUBNET_PURPOSE.format("vpc5"))
      }
    }

    context("VPC name is not specified but subnets are") {
      fixture {
        Fixture(
          createSubject(),
          createResource(null, "external (vpc5)")
        )
      }

      test("VPC name is derived from subnets") {
        expectThat(resolved.spec.locations.vpcName)
          .isEqualTo("vpc5")
      }
    }
  }
}

internal class ClusterNetworkResolverTests : NetworkResolverTests<ClusterSpec>() {
  override fun createSubject(): NetworkResolver<ClusterSpec> = ClusterNetworkResolver()

  override fun createResource(vpcName: String?, subnetPurpose: String?): Resource<ClusterSpec> = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "cluster",
    spec = ClusterSpec(
      moniker = Moniker(
        app = "fnord",
        stack = "test"
      ),
      imageProvider = ArtifactImageProvider(DeliveryArtifact("fnord", DEB)),
      locations = Locations(
        accountName = "test",
        vpcName = vpcName,
        subnet = subnetPurpose,
        regions = setOf(
          SubnetAwareRegionSpec(
            region = "us-west-2"
          )
        )
      ),
      _defaults = ServerGroupSpec(
        launchConfiguration = LaunchConfigurationSpec(
          instanceType = "m5.large",
          ebsOptimized = true,
          iamRole = "fnordInstanceProfile",
          instanceMonitoring = false
        ),
        capacity = Capacity(2, 2, 2),
        dependencies = ClusterDependencies(
          loadBalancerNames = setOf("fnord-internal"),
          securityGroupNames = setOf("fnord", "fnord-elb")
        ),
        health = HealthSpec(
          warmup = Duration.ofSeconds(120)
        )
      ),
      overrides = mapOf(
        "us-east-1" to ServerGroupSpec(
          launchConfiguration = LaunchConfigurationSpec(
            iamRole = "fnordEastInstanceProfile",
            keyPair = "fnord-keypair-325719997469-us-east-1"
          ),
          capacity = Capacity(5, 5, 5),
          dependencies = ClusterDependencies(
            loadBalancerNames = setOf("fnord-external"),
            securityGroupNames = setOf("fnord-ext")
          ),
          health = HealthSpec(
            healthCheckType = ELB
          )
        ),
        "us-west-2" to ServerGroupSpec(
          launchConfiguration = LaunchConfigurationSpec(
            keyPair = "fnord-keypair-${randomNumeric(12)}-us-west-2"
          )
        )
      )
    )
  )
}

internal class ClassicLoadBalancerNetworkResolverTests : NetworkResolverTests<ClassicLoadBalancerSpec>() {
  override fun createSubject(): NetworkResolver<ClassicLoadBalancerSpec> = ClassicLoadBalancerNetworkResolver()

  override fun createResource(vpcName: String?, subnetPurpose: String?): Resource<ClassicLoadBalancerSpec> = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "classic-load-balancer",
    spec = ClassicLoadBalancerSpec(
      moniker = Moniker(
        app = "fnord",
        stack = "test"
      ),
      locations = Locations(
        accountName = "test",
        vpcName = vpcName,
        subnet = subnetPurpose,
        regions = setOf(
          SubnetAwareRegionSpec(
            region = "us-west-2"
          )
        )
      ),
      healthCheck = ClassicLoadBalancerHealthCheck(
        target = "HTTP:7001/health"
      )
    )
  )
}

internal class ApplicationLoadBalancerNetworkResolverTests : NetworkResolverTests<ApplicationLoadBalancerSpec>() {
  override fun createSubject(): NetworkResolver<ApplicationLoadBalancerSpec> = ApplicationLoadBalancerNetworkResolver()

  override fun createResource(vpcName: String?, subnetPurpose: String?): Resource<ApplicationLoadBalancerSpec> = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "application-load-balancer",
    spec = ApplicationLoadBalancerSpec(
      moniker = Moniker(
        app = "fnord",
        stack = "test"
      ),
      locations = Locations(
        accountName = "test",
        vpcName = vpcName,
        subnet = subnetPurpose,
        regions = setOf(
          SubnetAwareRegionSpec(
            region = "us-west-2"
          )
        )
      ),
      listeners = emptySet(),
      targetGroups = emptySet()
    )
  )
}
