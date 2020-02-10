package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.logging.TracingSupport.Companion.withTracingContext
import com.netflix.spinnaker.keel.pause.ResourcePauser
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.veto.VetoEnforcer
import com.netflix.spinnaker.keel.veto.VetoResponse
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ResourceActuator(
  private val resourceRepository: ResourceRepository,
  private val diffFingerprintRepository: DiffFingerprintRepository,
  private val handlers: List<ResourceHandler<*, *>>,
  private val resourcePauser: ResourcePauser,
  private val vetoEnforcer: VetoEnforcer,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun <T : ResourceSpec> checkResource(resource: Resource<T>) {
    withTracingContext(resource) {
      val id = resource.id
      val plugin = handlers.supporting(resource.apiVersion, resource.kind)

      if (resourcePauser.isPaused(resource)) {
        log.debug("Actuation for resource {} is paused, skipping checks", id)
        publisher.publishEvent(ResourceCheckSkipped(resource.apiVersion, resource.kind, id, "ActuationPaused"))
        return@withTracingContext
      }

      if (plugin.actuationInProgress(resource)) {
        log.debug("Actuation for resource {} is already running, skipping checks", id)
        publisher.publishEvent(ResourceCheckSkipped(resource.apiVersion, resource.kind, id, "ActuationInProgress"))
        return@withTracingContext
      }

      try {
        val (desired, current) = plugin.resolve(resource)
        val diff = DefaultResourceDiff(desired, current)
        if (diff.hasChanges()) {
          diffFingerprintRepository.store(id, diff)
        }

        val response = vetoEnforcer.canCheck(resource)
        if (!response.allowed) {
          log.debug("Skipping actuation for resource {} because it was vetoed: {}", id, response.message)
          publisher.publishEvent(ResourceCheckSkipped(resource.apiVersion, resource.kind, id, response.vetoName))
          publishVetoedEvent(response, resource)
          return@withTracingContext
        }

        log.debug("Checking resource {}", id)

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
            val lastEvent = resourceRepository.lastEvent(id)
            if (lastEvent is ResourceDeltaDetected || lastEvent is ResourceActuationLaunched) {
              publisher.publishEvent(ResourceDeltaResolved(resource, clock))
            } else {
              publisher.publishEvent(ResourceValid(resource, clock))
            }
          }
        }
      } catch (e: ResourceCurrentlyUnresolvable) {
        log.warn("Resource check for {} failed (hopefully temporarily) due to {}", id, e.message)
        publisher.publishEvent(ResourceCheckUnresolvable(resource, e, clock))
      } catch (e: Exception) {
        log.error("Resource check for $id failed", e)
        publisher.publishEvent(ResourceCheckError(resource, e, clock))
      }
    }
  }

  private suspend fun ResourceHandler<*, *>.resolve(resource: Resource<out ResourceSpec>): Pair<Any, Any?> =
    supervisorScope {
      val desired = async {
        try {
          desired(resource)
        } catch (e: ResourceCurrentlyUnresolvable) {
          throw e
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

  /**
   * We want a specific status for specific types of vetos. This function publishes the
   * right event based on which veto said no.
   */
  private fun publishVetoedEvent(response: VetoResponse, resource: Resource<*>) =
    when {
      response.vetoName == "UnhappyVeto" -> {
        // don't publish an event, we want the status to stay as "unhappy" for clarity
      }
      else -> publisher.publishEvent(
        ResourceActuationVetoed(
          resource.apiVersion,
          resource.kind,
          resource.id,
          resource.spec.application,
          response.message,
          clock.instant()))
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

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ResourceSpec> ResourceHandler<S, *>.actuationInProgress(
    resource: Resource<*>
  ): Boolean =
    actuationInProgress(resource as Resource<S>)
  // end type coercing extensions
}
