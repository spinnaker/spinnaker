package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.RegionSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
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
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.MemoryCloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.ec2.resolvers.NetworkResolver.Companion.DEFAULT_SUBNET_PURPOSE
import com.netflix.spinnaker.keel.ec2.resolvers.NetworkResolver.Companion.DEFAULT_VPC_NAME
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import org.apache.commons.lang3.RandomStringUtils
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Duration

internal abstract class NetworkResolverTests<T : Locatable<*>> : JUnit5Minutests {

  protected abstract val createSubject: (CloudDriverCache) -> NetworkResolver<T>
  protected abstract fun createResource(
    vpc: String? = null,
    subnetPurpose: String? = null,
    eastZones: Set<String> = emptySet(),
    westZones: Set<String> = emptySet()
  ): Resource<T>

  data class Fixture<T : Locatable<*>>(
    val subjectFactory: (CloudDriverCache) -> NetworkResolver<T>,
    val resource: Resource<T>
  ) {
    private val vpc0East = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc0", resource.spec.locations.account, "us-east-1")
    private val vpc0West = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc0", resource.spec.locations.account, "us-west-2")
    private val vpc5East = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc5", resource.spec.locations.account, "us-east-1")
    private val vpc5West = Network(CLOUD_PROVIDER, "vpc-${randomHex()}", "vpc5", resource.spec.locations.account, "us-west-2")
    private val usEastSubnets =
      setOf(vpc0East, vpc5East).flatMap { vpc ->
        setOf("internal", "external").flatMap { purpose ->
          setOf("${vpc.region}c", "${vpc.region}d", "${vpc.region}e").map { zone ->
            Subnet(
              id = "subnet-${randomHex()}",
              vpcId = vpc.id,
              account = resource.spec.locations.account,
              region = vpc.region,
              availabilityZone = zone,
              purpose = "$purpose (${vpc.name})"
            )
          }
        }
      }
    private val usWestSubnets =
      setOf(vpc0West, vpc5West).flatMap { vpc ->
        setOf("internal", "external").flatMap { purpose ->
          setOf("${vpc.region}a", "${vpc.region}b", "${vpc.region}c").map { zone ->
            Subnet(
              id = "subnet-${randomHex()}",
              vpcId = vpc.id,
              account = resource.spec.locations.account,
              region = vpc.region,
              availabilityZone = zone,
              purpose = "$purpose (${vpc.name})"
            )
          }
        }
      }
    val subnets = (usEastSubnets + usWestSubnets).toSet()
    val allZones = subnets.map { it.availabilityZone }.distinct()
    val cloudDriverService = mockk<CloudDriverService>() {
      coEvery { listSubnets("aws") } returns subnets
    }
    val cloudDriverCache = MemoryCloudDriverCache(cloudDriverService)
    val subject = subjectFactory(cloudDriverCache)
    val resolved by lazy { subject(resource) }

    private fun randomHex(): String = RandomStringUtils.random(8, "0123456789abcdef")
  }

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(
        createSubject,
        createResource()
      )
    }

    test("supports the resource kind") {
      expectThat(listOf(subject).supporting(resource))
        .containsExactly(subject)
    }

    context("VPC name is not specified") {
      test("VPC name is defaulted") {
        expectThat(resolved.spec.locations.vpc)
          .isEqualTo(DEFAULT_VPC_NAME)
      }
    }

    context("VPC name is specified") {
      deriveFixture {
        copy(
          resource = createResource(vpc = "vpc5")
        )
      }

      test("specified VPC name is used") {
        expectThat(resolved.spec.locations.vpc)
          .isEqualTo("vpc5")
      }
    }
  }
}

internal abstract class SubnetAwareNetworkResolverTests<T : Locatable<SubnetAwareLocations>> : NetworkResolverTests<T>() {

  fun subnetAwareTests() = rootContext<Fixture<T>> {
    context("neither VPC name, subnet, or AZs are specified") {
      fixture {
        Fixture(
          createSubject,
          createResource()
        )
      }

      test("VPC name is defaulted") {
        expectThat(resolved.spec.locations.vpc)
          .isEqualTo(DEFAULT_VPC_NAME)
      }

      test("subnets are defaulted") {
        expectThat(resolved.spec.locations.subnet)
          .isEqualTo(DEFAULT_SUBNET_PURPOSE.format(DEFAULT_VPC_NAME))
      }
    }

    context("neither VPC name or subnet are specified but some AZs are") {
      fixture {
        Fixture(
          createSubject,
          createResource(westZones = setOf("us-west-2c"))
        )
      }

      test("VPC name is defaulted") {
        expectThat(resolved.spec.locations.vpc)
          .isEqualTo(DEFAULT_VPC_NAME)
      }

      test("subnets are defaulted") {
        expectThat(resolved.spec.locations.subnet)
          .isEqualTo(DEFAULT_SUBNET_PURPOSE.format(DEFAULT_VPC_NAME))
      }

      test("specified AZs are not re-assigned") {
        expectThat(resolved.spec.locations.regions["us-east-1"].availabilityZones)
          .hasSize(3)
      }

      test("specified AZs are not re-assigned") {
        expectThat(resolved.spec.locations.regions["us-west-2"].availabilityZones)
          .containsExactly("us-west-2c")
      }
    }

    context("VPC name is specified but subnets and AZs are not") {
      fixture {
        Fixture(
          createSubject,
          createResource(vpc = "vpc5")
        )
      }

      test("subnets are defaulted based on VPC name") {
        expectThat(resolved.spec.locations.subnet)
          .isEqualTo(DEFAULT_SUBNET_PURPOSE.format("vpc5"))
      }

      test("all AZs are assigned") {
        expectThat(resolved.spec.locations.regions.flatMap { it.availabilityZones })
          .containsExactlyInAnyOrder(allZones)
      }
    }

    context("VPC name is not specified but subnets are") {
      fixture {
        Fixture(
          createSubject,
          createResource(subnetPurpose = "external (vpc5)")
        )
      }

      test("VPC name is derived from subnets") {
        expectThat(resolved.spec.locations.vpc)
          .isEqualTo("vpc5")
      }

      test("all AZs are assigned") {
        expectThat(resolved.spec.locations.regions.flatMap { it.availabilityZones })
          .containsExactlyInAnyOrder(allZones)
      }
    }
  }
}

private operator fun <E : RegionSpec> Collection<E>.get(region: String): E =
  first { it.name == region }

internal class SecurityGroupNetworkResolverTests : NetworkResolverTests<SecurityGroupSpec>() {
  override val createSubject = ::SecurityGroupNetworkResolver

  override fun createResource(
    vpc: String?,
    subnetPurpose: String?,
    eastZones: Set<String>,
    westZones: Set<String>
  ): Resource<SecurityGroupSpec> =
    resource(
      apiVersion = SPINNAKER_EC2_API_V1,
      kind = "security-group",
      spec = SecurityGroupSpec(
        moniker = Moniker(
          app = "fnord",
          stack = "test"
        ),
        locations = SimpleLocations(
          account = "test",
          vpc = vpc,
          regions = setOf(
            SimpleRegionSpec(
              name = "us-east-1"
            ),
            SimpleRegionSpec(
              name = "us-west-2"
            )
          )
        ),
        description = "a security group"
      )
    )
}

internal class ClusterNetworkResolverTests : SubnetAwareNetworkResolverTests<ClusterSpec>() {
  override val createSubject = ::ClusterNetworkResolver

  override fun createResource(
    vpc: String?,
    subnetPurpose: String?,
    eastZones: Set<String>,
    westZones: Set<String>
  ): Resource<ClusterSpec> =
    resource(
      apiVersion = SPINNAKER_EC2_API_V1,
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker(
          app = "fnord",
          stack = "test"
        ),
        imageProvider = ArtifactImageProvider(DeliveryArtifact("fnord", DEB)),
        locations = SubnetAwareLocations(
          account = "test",
          vpc = vpc,
          subnet = subnetPurpose,
          regions = setOf(
            SubnetAwareRegionSpec(
              name = "us-east-1",
              availabilityZones = eastZones
            ),
            SubnetAwareRegionSpec(
              name = "us-west-2",
              availabilityZones = westZones
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
        )
      )
    )
}

internal class ClassicLoadBalancerNetworkResolverTests : SubnetAwareNetworkResolverTests<ClassicLoadBalancerSpec>() {
  override val createSubject = ::ClassicLoadBalancerNetworkResolver

  override fun createResource(
    vpc: String?,
    subnetPurpose: String?,
    eastZones: Set<String>,
    westZones: Set<String>
  ): Resource<ClassicLoadBalancerSpec> = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "classic-load-balancer",
    spec = ClassicLoadBalancerSpec(
      moniker = Moniker(
        app = "fnord",
        stack = "test"
      ),
      locations = SubnetAwareLocations(
        account = "test",
        vpc = vpc,
        subnet = subnetPurpose,
        regions = setOf(
          SubnetAwareRegionSpec(
            name = "us-east-1",
            availabilityZones = eastZones
          ),
          SubnetAwareRegionSpec(
            name = "us-west-2",
            availabilityZones = westZones
          )
        )
      ),
      healthCheck = ClassicLoadBalancerHealthCheck(
        target = "HTTP:7001/health"
      )
    )
  )
}

internal class ApplicationLoadBalancerNetworkResolverTests : SubnetAwareNetworkResolverTests<ApplicationLoadBalancerSpec>() {
  override val createSubject = ::ApplicationLoadBalancerNetworkResolver

  override fun createResource(
    vpc: String?,
    subnetPurpose: String?,
    eastZones: Set<String>,
    westZones: Set<String>
  ): Resource<ApplicationLoadBalancerSpec> = resource(
    apiVersion = SPINNAKER_EC2_API_V1,
    kind = "application-load-balancer",
    spec = ApplicationLoadBalancerSpec(
      moniker = Moniker(
        app = "fnord",
        stack = "test"
      ),
      locations = SubnetAwareLocations(
        account = "test",
        vpc = vpc,
        subnet = subnetPurpose,
        regions = setOf(
          SubnetAwareRegionSpec(
            name = "us-east-1",
            availabilityZones = eastZones
          ),
          SubnetAwareRegionSpec(
            name = "us-west-2",
            availabilityZones = westZones
          )
        )
      ),
      listeners = emptySet(),
      targetGroups = emptySet()
    )
  )
}
