package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ResourceActuator(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*, *>>,
  private val vetoEnforcer: VetoEnforcer,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun checkResource(id: ResourceId, apiVersion: ApiVersion, kind: String) {
    val response = vetoEnforcer.canCheck(id)
    if (!response.allowed) {
      log.debug("Skipping actuation for resource {} because it was vetoed: {}", id, response.message)
      publisher.publishEvent(ResourceCheckSkipped(apiVersion, kind, id))
      return
    }

    val plugin = handlers.supporting(apiVersion, kind)

    if (plugin.actuationInProgress(id)) {
      log.debug("Actuation for resource {} is already running, skipping checks", id)
      publisher.publishEvent(ResourceCheckSkipped(apiVersion, kind, id))
      return
    }

    log.debug("Checking resource {}", id)

    val resource = resourceRepository.get(id)

    try {
      val (desired, current) = plugin.resolve(resource)
      val diff = ResourceDiff(desired, current)

      when {
        current == null -> {
          log.warn("Resource {} is missing", id)
          publisher.publishEvent(ResourceMissing(resource, clock))

          plugin.create(resource, diff)
            .also { tasks ->
              publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        diff.hasChanges() -> {
          log.warn("Resource {} is invalid", id)
          log.info("Resource {} delta: {}", id, diff.toDebug())
          publisher.publishEvent(ResourceDeltaDetected(resource, diff.toDeltaJson(), clock))

          plugin.update(resource, diff)
            .also { tasks ->
              publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        else -> {
          log.info("Resource {} is valid", id)
          // TODO: not sure this logic belongs here
          val lastEvent = resourceRepository.eventHistory(resource.id, limit = 1).first()
          if (lastEvent is ResourceDeltaDetected || lastEvent is ResourceActuationLaunched) {
            publisher.publishEvent(ResourceDeltaResolved(resource, clock))
          } else {
            publisher.publishEvent(ResourceValid(resource, clock))
          }
        }
      }
    } catch (e: Exception) {
      log.error("Resource check for {} failed due to \"{}\"", id, e.message)
      publisher.publishEvent(ResourceCheckError(resource, e, clock))
    }
  }

  private suspend fun ResourceHandler<*, *>.resolve(resource: Resource<out ResourceSpec>): Pair<Any, Any?> =
    coroutineScope {
      val desired = async {
        try {
          desired(resource)
        } catch (e: Throwable) {
          throw CannotResolveDesiredState(resource.id, e)
        }
      }
      val current = async {
        try {
          current(resource)
        } catch (e: Throwable) {
          throw CannotResolveCurrentState(resource.id, e)
        }
      }
      desired.await() to current.await()
    }

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.desired(
    resource: Resource<*>
  ): R =
    desired(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.current(
    resource: Resource<*>
  ): R? =
    current(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.create(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<Task> =
    create(resource as Resource<S>, resourceDiff as ResourceDiff<R>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec, R : Any> ResourceHandler<S, R>.update(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<Task> =
    update(resource as Resource<S>, resourceDiff as ResourceDiff<R>)
  // end type coercing extensions
}
