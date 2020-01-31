package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.plugin.SimpleResourceHandler
import com.netflix.spinnaker.keel.plugin.SupportedKind
import java.util.UUID

const val TEST_API = "test.$SPINNAKER_API_V1"

fun resource(
  apiVersion: String = TEST_API,
  kind: String = "whatever",
  id: String = randomString(),
  application: String = "fnord"
): Resource<DummyResourceSpec> =
  DummyResourceSpec(id = id, application = application)
    .let { spec ->
      resource(
        apiVersion = apiVersion,
        kind = kind,
        spec = spec,
        id = spec.id,
        application = application
      )
    }

fun submittedResource(
  apiVersion: String = TEST_API,
  kind: String = "whatever",
  application: String = "fnord"
): SubmittedResource<DummyResourceSpec> =
  DummyResourceSpec(application = application)
    .let { spec ->
      submittedResource(
        apiVersion = apiVersion,
        kind = kind,
        spec = spec
      )
    }

fun locatableResource(
  apiVersion: String = TEST_API,
  kind: String = "locatable",
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
        apiVersion = apiVersion,
        kind = kind,
        spec = spec,
        id = spec.id,
        application = application
      )
    }

fun <T : Monikered> resource(
  apiVersion: String = TEST_API,
  kind: String = "whatever",
  spec: T
): Resource<T> = resource(
  apiVersion = apiVersion,
  kind = kind,
  spec = spec,
  id = spec.moniker.toString(),
  application = spec.application
)

fun <T : ResourceSpec> resource(
  apiVersion: String = TEST_API,
  kind: String = "whatever",
  spec: T,
  id: String = spec.id,
  application: String = "fnord"
): Resource<T> =
  Resource(
    apiVersion = apiVersion,
    kind = kind,
    spec = spec,
    metadata = mapOf(
      "id" to "${apiVersion.substringBefore(".")}:$kind:$id",
      "application" to application,
      "serviceAccount" to "keel@spinnaker"
    )
  )

fun <T : ResourceSpec> submittedResource(
  apiVersion: String = TEST_API,
  kind: String = "whatever",
  spec: T
): SubmittedResource<T> =
  SubmittedResource(
    apiVersion = apiVersion,
    kind = kind,
    metadata = mapOf(
      "serviceAccount" to "keel@spinnaker"
    ),
    spec = spec
  )

data class DummyResourceSpec(
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord"
) : ResourceSpec

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

object DummyResourceHandler : SimpleResourceHandler<DummyResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind(TEST_API, "whatever", DummyResourceSpec::class.java)

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }
}
