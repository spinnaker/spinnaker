package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.VersionedArtifactProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.generateId
import com.netflix.spinnaker.keel.api.plugins.SimpleResourceHandler
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import de.danielbechler.diff.inclusion.Inclusion.EXCLUDED
import de.danielbechler.diff.introspection.ObjectDiffProperty
import java.time.Duration
import java.util.UUID

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
        id = spec.id,
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
        id = spec.id,
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
  )
): Resource<DummyLocatableResourceSpec> =
  DummyLocatableResourceSpec(id = id, application = application, locations = locations)
    .let { spec ->
      resource(
        kind = kind,
        spec = spec,
        id = spec.id,
        application = application
      )
    }

fun <T : Monikered> resource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  spec: T
): Resource<T> = resource(
  kind = kind,
  spec = spec,
  id = spec.moniker.toString(),
  application = spec.application
)

fun <T : ResourceSpec> resource(
  kind: ResourceKind = TEST_API_V1.qualify("whatever"),
  spec: T,
  id: String = spec.id,
  application: String = "fnord"
): Resource<T> =
  Resource(
    kind = kind,
    spec = spec,
    metadata = mapOf(
      "id" to generateId(kind, spec),
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

enum class DummyEnum { VALUE }

data class DummyResourceSpec(
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord"
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
  override val locations: SimpleLocations = SimpleLocations(
    account = "test",
    vpc = "vpc0",
    regions = setOf(SimpleRegionSpec("us-west-1"))
  )
) : ResourceSpec, Locatable<SimpleLocations>

data class DummyArtifactVersionedResourceSpec(
  @get:ObjectDiffProperty(inclusion = EXCLUDED)
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord",
  override val artifactVersion: String? = "fnord-42.0",
  override val artifactName: String? = "fnord",
  override val artifactType: ArtifactType? = ArtifactType.deb
) : ResourceSpec, VersionedArtifactProvider

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

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }
}

object DummyResourceHandlerV2 : SimpleResourceHandler<DummyResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind(TEST_API_V2.qualify("whatever"), DummyResourceSpec::class.java)

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }
}
