package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.telemetry.ResourceChecked
import com.netflix.spinnaker.keel.telemetry.ResourceState.Diff
import com.netflix.spinnaker.keel.telemetry.ResourceState.Error
import com.netflix.spinnaker.keel.telemetry.ResourceState.Missing
import com.netflix.spinnaker.keel.telemetry.ResourceState.Ok
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
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) {
  suspend fun checkResource(name: ResourceName, apiVersion: ApiVersion, kind: String) {
    try {
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
          with(resource) {
            log.warn("Resource {} is missing", metadata.name)
            publisher.publishEvent(ResourceChecked(resource, Missing))

            resourceRepository.appendHistory(ResourceMissing(resource, clock))
          }

          plugin.create(resource, diff)
            .also { tasks ->
              resourceRepository.appendHistory(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        diff.hasChanges() -> {
          with(resource) {
            log.warn("Resource {} is invalid", metadata.name)
            log.info("Resource {} delta: {}", metadata.name, diff.toDebug())
            publisher.publishEvent(ResourceChecked(resource, Diff))

            resourceRepository.appendHistory(ResourceDeltaDetected(resource, diff.toDeltaJson(), clock))
          }

          plugin.update(resource, diff)
            .also { tasks ->
              resourceRepository.appendHistory(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        else -> {
          with(resource) {
            log.info("Resource {} is valid", metadata.name)
            val lastEvent = resourceRepository.eventHistory(resource.metadata.uid).first()
            if (lastEvent is ResourceDeltaDetected || lastEvent is ResourceActuationLaunched) {
              resourceRepository.appendHistory(ResourceDeltaResolved(resource, clock))
            }
            publisher.publishEvent(ResourceChecked(resource, Ok))
          }
        }
      }
    } catch (e: Exception) {
      log.error("Resource check for {} failed due to \"{}\"", name, e.message)
      publisher.publishEvent(ResourceChecked(apiVersion, kind, name, Error))
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
  ): List<TaskRef> =
    create(resource as Resource<S>, resourceDiff as ResourceDiff<R>)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.update(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<TaskRef> =
    update(resource as Resource<S>, resourceDiff as ResourceDiff<R>)
  // end type coercing extensions

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
