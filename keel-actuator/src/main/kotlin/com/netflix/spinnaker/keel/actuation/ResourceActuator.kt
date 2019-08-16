package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.telemetry.ResourceCheckError
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
  private val handlers: List<ResolvableResourceHandler<*, *>>,
  private val vetoEnforcer: VetoEnforcer,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  suspend fun checkResource(name: ResourceName, apiVersion: ApiVersion, kind: String) {
    try {
      val response = vetoEnforcer.canCheck(name)
      if (!response.allowed) {
        log.debug("Skipping actuation for resource {} because it was vetoed: {}", name, response.message)
        publisher.publishEvent(ResourceCheckSkipped(apiVersion, kind, name))
        return
      }

      val plugin = handlers.supporting(apiVersion, kind)

      if (plugin.actuationInProgress(name)) {
        log.debug("Actuation for resource {} is already running, skipping checks", name)
        publisher.publishEvent(ResourceCheckSkipped(apiVersion, kind, name))
        return
      }

      log.debug("Checking resource {}", name)

      val type = plugin.supportedKind.second
      val resource = resourceRepository.get(name, type)

      val (desired, current) = plugin.resolve(resource)
      val diff = ResourceDiff(desired, current)

      when {
        current == null -> {
          log.warn("Resource {} is missing", name)
          publisher.publishEvent(ResourceMissing(resource, clock))

          plugin.create(resource, diff)
            .also { tasks ->
              publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        diff.hasChanges() -> {
          log.warn("Resource {} is invalid", name)
          log.info("Resource {} delta: {}", name, diff.toDebug())
          publisher.publishEvent(ResourceDeltaDetected(resource, diff.toDeltaJson(), clock))

          plugin.update(resource, diff)
            .also { tasks ->
              publisher.publishEvent(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        else -> {
          log.info("Resource {} is valid", name)
          // TODO: not sure this logic belongs here
          val lastEvent = resourceRepository.eventHistory(resource.uid).first()
          if (lastEvent is ResourceDeltaDetected || lastEvent is ResourceActuationLaunched) {
            publisher.publishEvent(ResourceDeltaResolved(resource, current, clock))
          } else {
            publisher.publishEvent(ResourceValid(resource, clock))
          }
        }
      }
    } catch (e: Exception) {
      log.error("Resource check for {} failed due to \"{}\"", name, e.message)
      publisher.publishEvent(ResourceCheckError(apiVersion, kind, name, e))
    }
  }

  private suspend fun ResolvableResourceHandler<*, *>.resolve(resource: Resource<out Any>): Pair<Any, Any?> =
    coroutineScope {
      val desired = async { desired(resource) }
      val current = async { current(resource) }
      desired.await() to current.await()
    }

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.desired(
    resource: Resource<*>
  ): R =
    desired(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.current(
    resource: Resource<*>
  ): R? =
    current(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.create(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<Task> =
    create(resource as Resource<S>, resourceDiff as ResourceDiff<R>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.update(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<Task> =
    update(resource as Resource<S>, resourceDiff as ResourceDiff<R>)
  // end type coercing extensions
}
