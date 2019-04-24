package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.diff.toDebug
import com.netflix.spinnaker.keel.diff.toJson
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Missing
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.telemetry.ResourceChecked
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

  fun checkResource(name: ResourceName, apiVersion: ApiVersion, kind: String) {
    log.debug("Checking resource {}", name)

    val plugin = handlers.supporting(apiVersion, kind)
    val type = plugin.supportedKind.second
    val resource = resourceRepository.get(name, type)

    val desired = plugin.desired(resource)
    try {
      when (val current = plugin.current(resource)) {
        null -> {
          with(resource) {
            log.warn("Resource {} is missing", metadata.name)
            publisher.publishEvent(ResourceChecked(resource, Missing))

            resourceRepository.appendHistory(ResourceMissing(resource, clock))
          }

          plugin.create(resource)
            .also { tasks ->
              resourceRepository.appendHistory(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
            }
        }
        else -> {
          val diff = differ.compare(current, desired)
          if (diff.hasChanges()) {
            with(resource) {
              log.warn("Resource {} is invalid", metadata.name)
              log.info("Resource {} delta: {}", metadata.name, diff.toDebug(current, desired))
              publisher.publishEvent(ResourceChecked(resource, Diff))

              resourceRepository.appendHistory(ResourceDeltaDetected(resource, diff.toJson(current, desired), clock))
            }

            plugin.update(resource, ResourceDiff(current, diff))
              .also { tasks ->
                resourceRepository.appendHistory(ResourceActuationLaunched(resource, plugin.name, tasks, clock))
              }
          } else {
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
      }
    } catch (e: ResourceConflict) {
      log.error(
        "Resource {} current state could not be determined due to \"{}\"",
        resource.metadata.name,
        e.message
      )
    }
  }

  private val DiffNode.depth: Int
    get() = if (isRootNode) 0 else parentNode.depth + 1

  // These extensions get round the fact tht we don't know the spec type of the resource from
  // the repository. I don't want the `ResourceHandler` interface to be untyped though.
  @Suppress("UNCHECKED_CAST")
  private fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.desired(resource: Resource<*>): R? =
    desired(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.current(resource: Resource<*>): R? =
    current(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.create(resource: Resource<*>): List<TaskRef> =
    create(resource as Resource<S>)

  @Suppress("UNCHECKED_CAST")
  private fun <S : Any, R : Any> ResolvableResourceHandler<S, R>.update(
    resource: Resource<*>,
    resourceDiff: ResourceDiff<*>
  ): List<TaskRef> =
    update(resource as Resource<S>, resourceDiff as ResourceDiff<R>)
  // end type coercing extensions

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
