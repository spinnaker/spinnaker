package com.netflix.spinnaker.keel.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.HasApplication
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Named
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
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
  name: String = randomString(),
  application: String = "fnord"
): Resource<DummyResourceSpec> =
  DummyResourceSpec(name = name, application = application)
    .let { spec ->
      resource(
        apiVersion = apiVersion,
        kind = kind,
        spec = spec,
        name = spec.name,
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
  name = spec.moniker.name,
  application = spec.application
)

fun <T : Named> resource(
  apiVersion: ApiVersion = TEST_API,
  kind: String = "whatever",
  spec: T,
  name: String = spec.name,
  application: String = "fnord"
): Resource<T> =
  Resource(
    apiVersion = apiVersion,
    kind = kind,
    spec = spec,
    metadata = mapOf(
      "uid" to randomUID(),
      "name" to "${apiVersion.prefix}:$kind:$name",
      "application" to application,
      "serviceAccount" to "keel@spinnaker"
    )
  )

data class DummyResourceSpec(
  override val name: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord"
) : Named, HasApplication

data class DummyResource(
  val name: String = randomString(),
  val data: String = randomString()
) {
  constructor(spec: DummyResourceSpec) : this(spec.name, spec.data)
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

  override fun generateName(spec: DummyResourceSpec): ResourceName =
    ResourceName("test:whatever:${spec.name}")

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }

  override suspend fun delete(resource: Resource<DummyResourceSpec>) {
    TODO("not implemented")
  }

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
}
