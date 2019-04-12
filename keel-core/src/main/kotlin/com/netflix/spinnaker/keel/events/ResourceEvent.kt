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
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.UID
import java.time.Clock
import java.time.Instant

@JsonTypeInfo(
  use = Id.NAME,
  property = "type",
  include = As.PROPERTY
)
@JsonSubTypes(
  Type(value = ResourceCreated::class, name = "ResourceCreated"),
  Type(value = ResourceUpdated::class, name = "ResourceUpdated"),
  Type(value = ResourceMissing::class, name = "ResourceMissing"),
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

data class ResourceCreated @JsonCreator constructor(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent()

fun ResourceCreated(resource: Resource<*>, clock: Clock) = ResourceCreated(
  resource.metadata.uid,
  resource.apiVersion,
  resource.kind,
  resource.metadata.name.value,
  clock.instant()
)

fun ResourceCreated(resource: Resource<*>) = ResourceCreated(resource, ResourceEvent.clock)

data class ResourceUpdated(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, clock: Clock) : this(
    resource.metadata.uid,
    resource.apiVersion,
    resource.kind,
    resource.metadata.name.value,
    clock.instant()
  )

  constructor(resource: Resource<*>) : this(resource, clock)
}

data class ResourceMissing(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, clock: Clock) : this(
    resource.metadata.uid,
    resource.apiVersion,
    resource.kind,
    resource.metadata.name.value,
    clock.instant()
  )

  constructor(resource: Resource<*>) : this(resource, clock)
}

data class ResourceDeltaDetected(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, clock: Clock) : this(
    resource.metadata.uid,
    resource.apiVersion,
    resource.kind,
    resource.metadata.name.value,
    clock.instant()
  )

  constructor(resource: Resource<*>) : this(resource, clock)
}

data class ResourceDeltaResolved(
  override val uid: UID,
  override val apiVersion: ApiVersion,
  override val kind: String,
  override val name: String,
  override val timestamp: Instant
) : ResourceEvent() {
  constructor(resource: Resource<*>, clock: Clock) : this(
    resource.metadata.uid,
    resource.apiVersion,
    resource.kind,
    resource.metadata.name.value,
    clock.instant()
  )

  constructor(resource: Resource<*>) : this(resource, clock)
}
