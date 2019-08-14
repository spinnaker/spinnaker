package com.netflix.spinnaker.keel.test

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.HasApplication
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import java.util.UUID

val TEST_API = SPINNAKER_API_V1.subApi("test")

fun resource(
  apiVersion: ApiVersion = TEST_API,
  kind: String = "whatever",
  name: (DummyResourceSpec) -> String = DummyResourceSpec::name
): Resource<DummyResourceSpec> = resource(
  apiVersion = apiVersion,
  kind = kind,
  spec = DummyResourceSpec(),
  name = name
)

fun <T : Monikered> resource(
  apiVersion: ApiVersion = TEST_API,
  kind: String = "whatever",
  spec: T
): Resource<T> = resource(
  apiVersion = apiVersion,
  kind = kind,
  spec = spec,
  name = { it.moniker.name },
  application = { it.application }
)

fun <T : Any> resource(
  apiVersion: ApiVersion = TEST_API,
  kind: String = "whatever",
  spec: T,
  name: (T) -> String,
  application: (T) -> String = { "fnord" }
): Resource<T> =
  Resource(
    apiVersion = apiVersion,
    kind = kind,
    spec = spec,
    metadata = mapOf(
      "uid" to randomUID(),
      "name" to "${apiVersion.prefix}:$kind:${name(spec)}",
      "application" to application(spec),
      "serviceAccount" to "keel@spinnaker"
    )
  )

data class DummyResourceSpec(
  val name: String = randomString(),
  val data: String = randomString(),
  override val application: String = "fnord"
) : HasApplication

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
