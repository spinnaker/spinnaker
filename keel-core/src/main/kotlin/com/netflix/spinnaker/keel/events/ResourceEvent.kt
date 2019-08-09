/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
import java.time.Clock
import java.time.Instant

// todo emjburns: use the common class in kork, but refactor so you can also set the time in those.
@JsonTypeInfo(
  use = Id.NAME,
  property = "type",
  include = As.PROPERTY
)
@JsonSubTypes(
  Type(value = ResourceCreated::class, name = "ResourceCreated"),
  Type(value = ResourceUpdated::class, name = "ResourceUpdated"),
  Type(value = ResourceMissing::class, name = "ResourceMissing"),
  Type(value = ResourceActuationLaunched::class, name = "ResourceActuationLaunched"),
  Type(value = ResourceDeltaDetected::class, name = "ResourceDeltaDetected"),
  Type(value = ResourceDeltaResolved::class, name = "ResourceDeltaResolved")
)
sealed class ResourceEvent {
  abstract val uid: UID
  abstract val apiVersion: ApiVersion
  abstract val kind: String
  abstract val name: String
  abstract val timestamp: Instant

  companion object {
    val clock: Clock = Clock.systemDefaultZone()
  }
}

/**
 * A new resource was registered for management.
 */
data class ResourceCreated @JsonCreator constructor(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent()

fun ResourceCreated(resource: Resource<*>, clock: Clock) = ResourceCreated(
  resource.uid,
  resource.apiVersion,
  resource.kind,
  resource.name.value,
  clock.instant()
)

fun ResourceCreated(resource: Resource<*>) = ResourceCreated(resource, ResourceEvent.clock)

/**
 * The desired state of a resource was updated.
 *
 * @property delta The difference between the "base" spec (previous version) and "working" spec (the
 * updated version).
 */
data class ResourceUpdated(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  val delta: Map<String, Any?>,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, delta: Map<String, Any?>, clock: Clock) : this(
    resource.uid,
    resource.apiVersion,
    resource.kind,
    resource.name.value,
    delta,
    clock.instant()
  )

  constructor(resource: Resource<*>, delta: Map<String, Any?>) : this(resource, delta, clock)
}

/**
 * A managed resource does not currently exist in the cloud.
 */
data class ResourceMissing(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, clock: Clock) : this(
    resource.uid,
    resource.apiVersion,
    resource.kind,
    resource.name.value,
    clock.instant()
  )

  constructor(resource: Resource<*>) : this(resource, clock)
}

/**
 * A difference between the desired and actual state of a managed resource was detected.
 *
 * @property delta The difference between the "base" spec (desired) and "working" spec (actual).
 */
data class ResourceDeltaDetected(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  val delta: Map<String, Any?>,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, delta: Map<String, Any?>, clock: Clock) : this(
    resource.uid,
    resource.apiVersion,
    resource.kind,
    resource.name.value,
    delta,
    clock.instant()
  )

  constructor(resource: Resource<*>, delta: Map<String, Any?>) : this(resource, delta, clock)
}

/**
 * A task or tasks were launched to resolve a mismatch between desired and actual state of a managed
 * resource.
 */
data class ResourceActuationLaunched(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  val plugin: String,
  val tasks: List<TaskRef>,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, plugin: String, tasks: List<TaskRef>, clock: Clock) :
    this(
      resource.uid,
      resource.apiVersion,
      resource.kind,
      resource.name.value,
      plugin,
      tasks,
      clock.instant()
    )

  constructor(resource: Resource<*>, plugin: String, tasks: List<TaskRef>) :
    this(resource, plugin, tasks, clock)
}

/**
 * The desired and actual states of a managed resource now match where previously there was a delta
 * (or the resource did not exist).
 */
data class ResourceDeltaResolved(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant,
  val desired: Any,
  val current: Any
) : ResourceEvent() {
  constructor(resource: Resource<*>, current: Any, clock: Clock) :
    this(
      resource.uid,
      resource.apiVersion,
      resource.kind,
      resource.name.value,
      clock.instant(),
      resource.spec,
      current
    )

  constructor(resource: Resource<*>, current: Any) :
    this(resource, current, clock)
}

/**
 * The reference to a task launched (currently always in Orca) to resolve a difference between the
 * desired and actual states of a managed resource.
 */
@JsonSerialize(using = ToStringSerializer::class)
@JsonDeserialize(using = TaskRefDeserializer::class)
data class TaskRef(val value: String) {
  override fun toString(): String = value
}

class TaskRefDeserializer : StdDeserializer<TaskRef>(TaskRef::class.java) {
  override fun deserialize(parser: JsonParser, context: DeserializationContext): TaskRef =
    TaskRef(parser.valueAsString)
}
