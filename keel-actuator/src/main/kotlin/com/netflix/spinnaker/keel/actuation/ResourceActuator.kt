package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.diff.toDebug
import com.netflix.spinnaker.keel.diff.toDeltaJson
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResolvedResource
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.telemetry.ResourceCheckSkipped
import com.netflix.spinnaker.keel.telemetry.ResourceChecked
import com.netflix.spinnaker.keel.telemetry.ResourceState.Diff
import com.netflix.spinnaker.keel.telemetry.ResourceState.Error
import com.netflix.spinnaker.keel.telemetry.ResourceState.Missing
import com.netflix.spinnaker.keel.telemetry.ResourceState.Ok
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.node.DiffNode
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

  private val differ = ObjectDifferBuilder.buildDefault()

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
      val diff = differ.compare(desired, current)

      when {
        current == null -> {
          with(resource) {
            log.warn("Resource {} is missing", metadata.name)
            publisher.publishEvent(ResourceChecked(resource, Missing))

            resourceRepository.appendHistory(ResourceMissing(resource, clock))
          }

          plugin.create(resource, ResourceDiff(desired, current, diff))
            .also { tasks ->
              resourceRepository.appendHistory(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        diff.hasChanges() -> {
          with(resource) {
            log.warn("Resource {} is invalid", metadata.name)
            log.info("Resource {} delta: {}", metadata.name, diff.toDebug(desired, current))
            publisher.publishEvent(ResourceChecked(resource, Diff))

            resourceRepository.appendHistory(ResourceDeltaDetected(resource, diff.toDeltaJson(desired, current), clock))
          }

          plugin.update(resource, ResourceDiff(desired, current, diff))
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

  private val DiffNode.depth: Int
    get() = if (isRootNode) 0 else parentNode.depth + 1

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.resolve(
    resource: Resource<*>
  ): ResolvedResource<R> =
    resolve(resource as Resource<S>)

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
