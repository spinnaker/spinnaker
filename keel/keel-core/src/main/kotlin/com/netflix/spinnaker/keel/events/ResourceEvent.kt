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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonProperty.Access
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.enforcers.ActiveVerifications
import com.netflix.spinnaker.keel.events.ResourceState.Diff
import com.netflix.spinnaker.keel.events.ResourceState.Error
import com.netflix.spinnaker.keel.events.ResourceState.Missing
import com.netflix.spinnaker.keel.events.ResourceState.Ok
import com.netflix.spinnaker.keel.events.ResourceState.Unresolvable
import com.netflix.spinnaker.keel.events.EventLevel.WARNING
import com.netflix.spinnaker.keel.events.EventLevel.ERROR
import com.netflix.spinnaker.keel.events.EventLevel.SUCCESS
import com.netflix.spinnaker.keel.persistence.ResourceStatus
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.exceptions.UserException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
  Type(value = ResourceDeleted::class, name = "ResourceDeleted"),
  Type(value = ResourceMissing::class, name = "ResourceMissing"),
  Type(value = ResourceActuationLaunched::class, name = "ResourceActuationLaunched"),
  Type(value = ResourceDeltaDetected::class, name = "ResourceDeltaDetected"),
  Type(value = ResourceDeltaResolved::class, name = "ResourceDeltaResolved"),
  Type(value = ResourceValid::class, name = "ResourceValid"),
  Type(value = ResourceCheckError::class, name = "ResourceCheckError"),
  Type(value = ResourceCheckUnresolvable::class, name = "ResourceCheckUnresolvable"),
  Type(value = ResourceActuationPaused::class, name = "ResourceActuationPaused"),
  Type(value = ResourceActuationResumed::class, name = "ResourceActuationResumed"),
  Type(value = ResourceActuationVetoed::class, name = "ResourceActuationVetoed"),
  Type(value = ResourceTaskFailed::class, name = "ResourceTaskFailed"),
  Type(value = ResourceTaskSucceeded::class, name = "ResourceTaskSucceeded"),
  Type(value = ResourceDiffNotActionable::class, name = "ResourceDiffNotActionable"),
  Type(value = VerificationBlockedActuation::class, name = "VerificationBlockedActuation"),
  Type(value = MaxResourceDeletionAttemptsReached::class, name = "MaxResourceDeletionAttemptsReached"),
  Type(value = ResourceDeletionLaunched::class, name = "ResourceDeletionLaunched")
)
abstract class ResourceEvent(
  open val message: String? = null,
  override val triggeredBy: String? = null
) : PersistentEvent(), ResourceHistoryEvent {
  override val scope = EventScope.RESOURCE
  abstract val kind: ResourceKind
  abstract val id: String
  abstract val version: Int

  override val ref: String
    get() = id
}

/**
 * A new resource was registered for management.
 */
data class ResourceCreated(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val displayName: String = "Resource definition created",
) : ResourceEvent() {

  constructor(resource: Resource<*>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant()
  )
}

/**
 * The desired state of a resource was updated.
 *
 * @property delta The difference between the "base" spec (previous version) and "working" spec (the
 * updated version).
 */
data class ResourceUpdated(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val delta: Map<String, Any?>,
  override val timestamp: Instant,
  override val displayName: String = "Resource definition updated",
) : ResourceEvent() {
  constructor(resource: Resource<*>, delta: Map<String, Any?>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    delta,
    clock.instant()
  )
}

data class ResourceDeleted(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val displayName: String = "Resource definition deleted",
) : ResourceEvent() {
  constructor(resource: Resource<*>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant()
  )
}

sealed class ResourceCheckResult(
  override val message: String? = null
) : ResourceEvent(message = message) {
  abstract val state: ResourceState

  @JsonIgnore
  override val ignoreRepeatedInHistory = true
}

/**
 * A managed resource does not currently exist in the cloud.
 */
data class ResourceMissing(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val displayName: String = "Resource not found",
) : ResourceCheckResult() {
  @JsonIgnore
  override val state = Missing

  constructor(resource: Resource<*>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant()
  )
}

/**
 * A difference between the desired and actual state of a managed resource was detected.
 *
 * @property delta The difference between the "base" spec (desired) and "working" spec (actual).
 */
data class ResourceDeltaDetected(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val delta: Map<String, Any?>,
  override val timestamp: Instant,
  override val displayName: String = "Resource does not match current definition",
) : ResourceCheckResult() {
  @JsonIgnore
  override val state = Diff

  constructor(resource: Resource<*>, delta: Map<String, Any?>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    delta,
    clock.instant()
  )
}

/**
 * A task or tasks were launched to resolve a mismatch between desired and actual state of a managed
 * resource.
 */
data class ResourceActuationLaunched(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val plugin: String,
  val tasks: List<Task>,
  override val timestamp: Instant,

  override val displayName: String = "Updating resource to match current definition",
) : ResourceEvent() {
  constructor(resource: Resource<*>, plugin: String, tasks: List<Task>, clock: Clock = Companion.clock) :
    this(
      resource.kind,
      resource.id,
      resource.version,
      resource.application,
      plugin,
      tasks,
      clock.instant()
    )
}

/**
 * Actuation on the managed resource has been paused.
 */
data class ResourceActuationPaused(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val triggeredBy: String?,
  override val displayName: String = "Resource management paused",
) : ResourceEvent() {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, timestamp: Instant, triggeredBy: String) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    timestamp,
    triggeredBy
  )

  constructor(resource: Resource<*>, triggeredBy: String, clock: Clock = Companion.clock) : this(
    resource,
    clock.instant(),
    triggeredBy
  )
}

/**
 * Actuation on the managed resource has been vetoed.
 *
 * @property reason The reason why actuation was vetoed.
 * @property veto The veto that vetoed this resource
 */
data class ResourceActuationVetoed(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val reason: String?,
  val veto: String? = null,
  val suggestedStatus: ResourceStatus? = null,
  override val timestamp: Instant,
  override val level: EventLevel = WARNING,
  override val displayName: String = "Unable to update resource to match current definition${if (reason != null) " - $reason" else ""}",
) : ResourceEvent(message = reason) {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, reason: String?, veto: String? = null, suggestedStatus: ResourceStatus? = null, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    reason,
    veto,
    suggestedStatus,
    clock.instant()
  )
}

/**
 * Actuation on the managed resource has resumed.
 */
data class ResourceActuationResumed(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val triggeredBy: String?,
  override val timestamp: Instant,
  override val displayName: String = "Resource management resumed",
) : ResourceEvent() {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, triggeredBy: String, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    triggeredBy,
    clock.instant()
  )
}

/**
 * Orca task the managed resource has failed.
 *
 * @property reason The reason why actuation failed.
 */
data class ResourceTaskFailed(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val reason: String?,
  val tasks: List<Task> = emptyList(),
  override val timestamp: Instant,
  override val level: EventLevel = ERROR,
  override val displayName: String = "Failed to update resource to match definition${if (reason != null) " - $reason" else ""}",
) : ResourceEvent(message = reason) {

  constructor(resource: Resource<*>, reason: String?, tasks: List<Task>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    reason,
    tasks,
    clock.instant()
  )
}

/**
 * Orca task for the managed resource has succeeded.
 */
data class ResourceTaskSucceeded(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val tasks: List<Task> = emptyList(),
  override val timestamp: Instant,
  override val level: EventLevel = SUCCESS,
  override val displayName: String = "Resource update task succeeded",
) : ResourceEvent() {

  constructor(resource: Resource<*>, tasks: List<Task>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    tasks,
    clock.instant()
  )
}

/**
 * The desired and actual states of a managed resource now match where previously there was a delta
 * (or the resource did not exist).
 */
data class ResourceDeltaResolved(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val level: EventLevel = SUCCESS,
  override val displayName: String = "Resource was matched to its definition successfully",
) : ResourceCheckResult() {
  @JsonIgnore
  override val state = Ok

  constructor(resource: Resource<*>, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant()
  )
}

data class ResourceValid(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val level: EventLevel = SUCCESS,
  override val displayName: String = "Resource matches its definition",
) : ResourceCheckResult() {
  @JsonIgnore
  override val state = Ok

  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, clock: Clock = Companion.clock) :
    this(
      resource.kind,
      resource.id,
      resource.version,
      resource.application,
      clock.instant()
    )
}

data class ResourceCheckUnresolvable(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val message: String?,
  override val level: EventLevel = WARNING,
  override val displayName: String = "Unable to compare resource to its definition${if (message != null) " - $message" else ""}",
) : ResourceCheckResult(message = message) {
  @JsonIgnore
  override val state = Unresolvable

  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, exception: ResourceCurrentlyUnresolvable, clock: Clock = Companion.clock) :
    this(
      resource.kind,
      resource.id,
      resource.version,
      resource.application,
      clock.instant(),
      exception.message
    )
}

enum class ResourceCheckErrorOrigin {
  @JsonProperty("user")
  USER,

  @JsonProperty("system")
  SYSTEM,

  @JsonProperty("unknown")
  UNKNOWN;

  companion object {
    val log: Logger by lazy { LoggerFactory.getLogger(ResourceCheckErrorOrigin::class.java) }
    fun fromException(exceptionType: Class<out SpinnakerException>) =
      when {
        UserException::class.java.isAssignableFrom(exceptionType) -> USER
        SystemException::class.java.isAssignableFrom(exceptionType) -> SYSTEM
        else -> UNKNOWN.also {
          log.trace(
            "All keel exceptions should inherit from UserException or SystemException (got ${exceptionType.name}). " +
              "This is a probably a bug."
          )
        }
      }
  }
}

data class ResourceCheckError(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  val exceptionType: Class<out SpinnakerException>,
  val exceptionMessage: String?,
  override val displayName: String = "Failed to check resource status",
  override val level: EventLevel = ERROR,
) : ResourceCheckResult(message = exceptionMessage) {
  @JsonIgnore
  override val state = Error

  @JsonProperty(access = Access.READ_ONLY)
  val origin: ResourceCheckErrorOrigin = ResourceCheckErrorOrigin.fromException(exceptionType)

  constructor(resource: Resource<*>, exception: SpinnakerException, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant(),
    exception.javaClass,
    exception.message
  )
}

data class ResourceDiffNotActionable(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val message: String?,
  override val level: EventLevel = WARNING,
  override val displayName: String = "Unable to update resource to match current definition${if (message != null) " - $message" else ""}",
) : ResourceEvent() {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, message: String?, clock: Clock = Companion.clock) : this(
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant(),
    message
  )
}

data class VerificationBlockedActuation(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  override val timestamp: Instant,
  override val message: String?,
  override val displayName: String = "Waiting for verification to complete before updating can start",
) : ResourceEvent() {
  @JsonIgnore
  override val ignoreRepeatedInHistory = true

  constructor(resource: Resource<*>, e: ActiveVerifications, clock: Clock = Companion.clock) : this (
    resource.kind,
    resource.id,
    resource.version,
    resource.application,
    clock.instant(),
    message = if(e.active.count() == 1) {
      "there is an active verification against version ${e.active.first().version}"
    } else {
      "there are active verifications against versions ${e.active.map { it.version }}"
    }
  )
}

/**
 * A [ResourceEvent] that signals the maximum allowed number of deletion attempts for the resource has been reached.
 *
 * @see EnvironmentDeletionConfiguration
 */
class MaxResourceDeletionAttemptsReached(
  override val application: String,
  override val timestamp: Instant,
  override val displayName: String,
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  val attempts: Int
) : ResourceEvent() {
  constructor(resource: Resource<*>, attempts: Int, clock: Clock = Companion.clock) : this (
    resource.application,
    clock.instant(),
    resource.displayName,
    resource.kind,
    resource.id,
    resource.version,
    attempts
  )
  override val message: String = "Failed to delete resource after maximum number of attempts ($attempts)"
}

/**
 * A task or tasks were launched to delete a managed resource. This should be the very last event
 * ever recorded for a resource before being removed from the database.
 */
data class ResourceDeletionLaunched(
  override val kind: ResourceKind,
  override val id: String,
  override val version: Int,
  override val application: String,
  val plugin: String,
  val tasks: List<Task>,
  override val timestamp: Instant,
  override val displayName: String = "Deleting resource",
) : ResourceEvent() {
  constructor(resource: Resource<*>, plugin: String, tasks: List<Task>, clock: Clock = Companion.clock) :
    this(
      resource.kind,
      resource.id,
      resource.version,
      resource.application,
      plugin,
      tasks,
      clock.instant()
    )
}
