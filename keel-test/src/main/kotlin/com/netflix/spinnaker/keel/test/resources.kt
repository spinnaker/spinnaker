package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.api.AccountAwareLocations
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SimpleRegions
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.ec2.ApplicationLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerHealthCheck
import com.netflix.spinnaker.keel.api.ec2.ClassicLoadBalancerSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_APPLICATION_LOAD_BALANCER_V1_2
import com.netflix.spinnaker.keel.api.ec2.EC2_CLASSIC_LOAD_BALANCER_V1
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.LoadBalancerDependencies
import com.netflix.spinnaker.keel.api.generateId
import com.netflix.spinnaker.keel.api.plugins.SimpleResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.titus.TITUS_CLUSTER_V1
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import io.mockk.mockk
import java.time.Duration
import java.util.*

val TEST_API_V1 = ApiVersion("test", "1")
val TEST_API_V2 = ApiVersion("test", "2")

fun resource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  id: String = randomString(),
  application: String = "fnord"
): Resource<DummyResourceSpec> =
  DummyResourceSpec(id = id, application = application)
    .let { spec ->
      resource(
        kind = kind,
        spec = spec,
        application = application
      )
    }

fun artifactVersionedResource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  id: String = randomString(),
  application: String = "fnord"
): Resource<DummyArtifactVersionedResourceSpec> =
  DummyArtifactVersionedResourceSpec(id = id, application = application)
    .let { spec ->
      resource(
        kind = kind,
        spec = spec,
        application = application
      )
    }

fun submittedResource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  application: String = "fnord"
): SubmittedResource<DummyResourceSpec> =
  DummyResourceSpec(application = application)
    .let { spec ->
      submittedResource(
        kind = kind,
        spec = spec
      )
    }

fun locatableResource(
  kind: ResourceKind = TEST_API_V1.qualify("locatable"),
  id: String = randomString(),
  application: String = "fnord",
  locations: SimpleLocations = SimpleLocations(
    account = "test",
    vpc = "vpc0",
    regions = setOf(SimpleRegionSpec("us-west-1"))
  ),
  moniker: Moniker = Moniker("fnord", "locatable", "dummy")
): Resource<DummyLocatableResourceSpec> =
  resource(
    kind = kind,
    spec = DummyLocatableResourceSpec(id = id, application = application, locations = locations, moniker = moniker),
    application = application
  )

fun dependentResource(
  kind: ResourceKind = TEST_API_V1.qualify("dependent"),
  id: String = randomString(),
  application: String = "fnord",
  locations: SimpleLocations = SimpleLocations(
    account = "test",
    vpc = "vpc0",
    regions = setOf(SimpleRegionSpec("us-west-1"))
  ),
  moniker: Moniker = Moniker("fnord", "dependent", "dummy"),
  dependsOn: Set<Dependency> = emptySet()
): Resource<DummyDependentResourceSpec> =
  resource(
    kind = kind,
    spec = DummyDependentResourceSpec(id = id, application = application, locations = locations, moniker = moniker, dependsOn = dependsOn),
    application = application
  )

fun <T : Monikered> resource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  spec: T
): Resource<T> = resource(
  kind = kind,
  spec = spec,
  application = spec.application
)

fun <T : ResourceSpec> resource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  spec: T,
  application: String = "fnord"
): Resource<T> =
  Resource(
    kind = kind,
    spec = spec,
    metadata = mapOf(
      "id" to generateId(kind, spec),
      "version" to 1,
      "application" to application,
      "serviceAccount" to "keel@spinnaker"
    )
  )

fun <T : ResourceSpec> submittedResource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  spec: T
): SubmittedResource<T> =
  SubmittedResource(
    kind = kind,
    metadata = mapOf(
      "serviceAccount" to "keel@spinnaker"
    ),
    spec = spec
  )

fun versionedArtifactResource(
  kind: ResourceKind = TEST_API_V1.qualify("versionedArtifact"),
  id: String = randomString(),
  application: String = "fnord"
): Resource<DummyArtifactVersionedResourceSpec> =
  DummyArtifactVersionedResourceSpec(id = id, application = application)
    .let { spec ->
      resource(
        kind = kind,
        spec = spec,
        application = application
      )
    }

fun artifactReferenceResource(
  kind: ResourceKind = TEST_API_V1.qualify("artifactReference"),
  id: String = randomString(),
  application: String = "fnord",
  artifactReference: String = "fnord",
  artifactType: ArtifactType = DEBIAN
): Resource<DummyArtifactReferenceResourceSpec> =
  DummyArtifactReferenceResourceSpec(id = id, application = application, artifactReference = artifactReference, artifactType = artifactType)
    .let { spec ->
      resource(
        kind = kind,
        spec = spec,
        application = application
      )
    }

enum class DummyEnum { VALUE }

data class DummyResourceSpec(
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val displayName: String = "fnord-dummy"
) : ResourceSpec {
  val intData: Int = 1234
  val boolData: Boolean = true
  val timeData: Duration = Duration.ofMinutes(5)
  val enumData: DummyEnum = DummyEnum.VALUE
}

data class DummyLocatableResourceSpec(
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val displayName: String = "fnord-locatable-dummy",
  override val locations: SimpleLocations = SimpleLocations(
    account = "test",
    vpc = "vpc0",
    regions = setOf(SimpleRegionSpec("us-west-1"))
  ),
  override val moniker: Moniker = Moniker("fnord", "locatable", "dummy")
) : ResourceSpec, Locatable<SimpleLocations>, Monikered

data class DummyDependentResourceSpec(
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val displayName: String = "fnord-dependent-dummy",
  override val locations: SimpleLocations = SimpleLocations(
    account = "test",
    vpc = "vpc0",
    regions = setOf(SimpleRegionSpec("us-west-1"))
  ),
  override val moniker: Moniker = Moniker("fnord", "dependent", "dummy"),
  override val dependsOn: Set<Dependency>
) : ResourceSpec, Locatable<SimpleLocations>, Monikered, Dependent

data class DummyArtifactVersionedResourceSpec(
  @get:ExcludedFromDiff
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val artifactVersion: String? = "fnord-42.0",
  override val artifactName: String? = "fnord",
  override val artifactType: ArtifactType? = DEBIAN,
  override val displayName: String = "fnord-artifact-versioned-dummy",
) : ResourceSpec, VersionedArtifactProvider

data class DummyArtifactReferenceResourceSpec(
  @get:ExcludedFromDiff
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val artifactType: ArtifactType? = DEBIAN,
  override val artifactReference: String? = "fnord",
  override val displayName: String = "fnord-artifact-reference-dummy",
  override val moniker: Moniker = Moniker("fnord", "artifactReference", "dummy"),
  override val locations: SimpleRegions = SimpleRegions(setOf(SimpleRegionSpec("us-east-1")))
) : ResourceSpec, ArtifactReferenceProvider, Monikered, Locatable<SimpleRegions>

data class DummyResource(
  val id: String = randomString(),
  val data: String = randomString()
) {
  constructor(spec: DummyResourceSpec) : this(spec.id, spec.data)
}

fun randomString(length: Int = 8) =
  UUID.randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)

object DummyResourceHandlerV1 : SimpleResourceHandler<DummyResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind(TEST_API_V1.qualify("whatever"), DummyResourceSpec::class.java)

  override val eventPublisher: EventPublisher = mockk(relaxed = true)

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }
}

object DummyResourceHandlerV2 : SimpleResourceHandler<DummyResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind(TEST_API_V2.qualify("whatever"), DummyResourceSpec::class.java)

  override val eventPublisher: EventPublisher = mockk(relaxed = true)

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }
}

object DummyLocatableResourceHandler : SimpleResourceHandler<DummyLocatableResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind(TEST_API_V1.qualify("locatable"), DummyLocatableResourceSpec::class.java)

  override val eventPublisher: EventPublisher = mockk(relaxed = true)
}

object DummyDependentResourceHandler : SimpleResourceHandler<DummyDependentResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind(TEST_API_V1.qualify("dependent"), DummyDependentResourceSpec::class.java)

  override val eventPublisher: EventPublisher = mockk(relaxed = true)
}

object DummyAccountAwareLocations : AccountAwareLocations<SimpleRegionSpec> {
  override val account: String = "test"
  override val regions: Set<SimpleRegionSpec> = setOf(SimpleRegionSpec("us-east-1"))
}

class DummyComputeResourceSpec(
  @get:ExcludedFromDiff
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val artifactType: ArtifactType? = DEBIAN,
  override val artifactReference: String? = "fnord",
  override val artifactName: String? = "fnord",
  override val artifactVersion: String? = "fnord-1.0.0",
  override val displayName: String = "fnord-dummy-compute-resource",
  override val moniker: Moniker = Moniker("fnord", "computeResource", "dummy"),
  override val locations: DummyAccountAwareLocations = DummyAccountAwareLocations
) : ComputeResourceSpec<DummyAccountAwareLocations>

fun computeResource(
  kind: ResourceKind = TEST_API_V1.qualify("compute"),
  spec: DummyComputeResourceSpec = DummyComputeResourceSpec()
): Resource<DummyComputeResourceSpec> =
  resource(kind, spec)

object DummyResourceSpecIdentifier : ResourceSpecIdentifier(
  kind<DummyLocatableResourceSpec>("test/locatable@v1"),
  kind<DummyResourceSpec>("test/whatever@v1")
)

fun resourceFactory(
  resourceSpecIdentifier: ResourceSpecIdentifier = DummyResourceSpecIdentifier,
  specMigrators: List<SpecMigrator<*, *>> = emptyList()
) = ResourceFactory(
  objectMapper = configuredObjectMapper(),
  resourceSpecIdentifier,
  specMigrators = specMigrators
)

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
  )
)

fun ec2Cluster(
  moniker: Moniker = Moniker("fnord", "test"),
  artifact: DebianArtifact = debianArtifact()
) = resource(
  kind = EC2_CLUSTER_V1_1.kind,
  spec = ec2ClusterSpec.copy(
    moniker = moniker,
    artifactReference = artifact.reference
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
  )
)

fun titusCluster(
  moniker: Moniker = Moniker("fnord", "test"),
  artifact: DockerArtifact = dockerArtifact()
) = resource(
  kind = TITUS_CLUSTER_V1.kind,
  spec = titusClusterSpec.copy(
    moniker = moniker,
    container = ReferenceProvider(reference = artifact.reference)
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

fun applicationLoadBalancer() = resource(
  kind = EC2_APPLICATION_LOAD_BALANCER_V1_2.kind,
  spec = albSpec
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

fun classicLoadBalancer() = resource(
  kind = EC2_CLASSIC_LOAD_BALANCER_V1.kind,
  spec = clbSpec
)
