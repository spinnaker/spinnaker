package com.netflix.spinnaker.keel.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

val TEST_API = SPINNAKER_API_V1.subApi("test")

fun resource(
  apiVersion: ApiVersion = TEST_API,
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

fun <T : Monikered> resource(
  apiVersion: ApiVersion = TEST_API,
  kind: String = "whatever",
  spec: T
): Resource<T> = resource(
  apiVersion = apiVersion,
  kind = kind,
  spec = spec,
  id = spec.moniker.name,
  application = spec.application
)

fun <T : ResourceSpec> resource(
  apiVersion: ApiVersion = TEST_API,
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
      "uid" to randomUID(),
      "id" to "${apiVersion.prefix}:$kind:$id",
      "application" to application,
      "serviceAccount" to "keel@spinnaker"
    )
  )

data class DummyResourceSpec(
  override val id: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord"
) : ResourceSpec

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

object DummyResourceHandler : ResourceHandler<DummyResourceSpec> {
  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("test")

  override val supportedKind: Pair<ResourceKind, Class<DummyResourceSpec>> =
    ResourceKind("test", "whatever", "whatevers") to DummyResourceSpec::class.java

  override val objectMapper: ObjectMapper = configuredObjectMapper()

  override val normalizers: List<ResourceNormalizer<*>> = emptyList()

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }

  override suspend fun delete(resource: Resource<DummyResourceSpec>) {
    TODO("not implemented")
  }

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
}
