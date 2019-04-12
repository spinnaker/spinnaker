package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Missing
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceHandler.ResourceDiff
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
  private val handlers: List<ResourceHandler<*>>,
  private val publisher: ApplicationEventPublisher,
  private val clock: Clock
) {

  private val differ = ObjectDifferBuilder.buildDefault()

  fun checkResource(name: ResourceName, apiVersion: ApiVersion, kind: String) {
    log.debug("Checking resource {}", name)

    val plugin = handlers.supporting(apiVersion, kind)
    val type = plugin.supportedKind.second
    val resource = resourceRepository.get(name, type)
    try {
      when (val current = plugin.current(resource)) {
        null -> {
          with(resource) {
            log.warn("Resource {} is missing", metadata.name)
            resourceRepository.appendHistory(ResourceMissing(resource, clock))
            publisher.publishEvent(ResourceChecked(resource, Missing))
          }

          plugin.create(resource)
        }
        else -> {
          val diff = differ.compare(current, resource.spec)
          if (diff.hasChanges()) {
            val builder = StringBuilder()
            diff.visit { node, _ ->
              builder
                .append("".padStart(node.depth * 2))
                .append(node.toString())
                .append("\n")
            }
            with(resource) {
              log.warn("Resource {} is invalid", metadata.name)
              log.info("Resource {} delta: {}", metadata.name, builder.toString())
              resourceRepository.appendHistory(ResourceDeltaDetected(resource, clock))
              publisher.publishEvent(ResourceChecked(resource, Diff))
            }

            plugin.update(resource, ResourceDiff(current, diff))
          } else {
            with(resource) {
              log.info("Resource {} is valid", metadata.name)
              if (resourceRepository.eventHistory(resource.metadata.uid).first() is ResourceDeltaDetected) {
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
  private fun <T : Any> ResourceHandler<T>.current(resource: Resource<*>): T? =
    current(resource as Resource<T>)

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> ResourceHandler<T>.create(resource: Resource<*>) {
    create(resource as Resource<T>)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> ResourceHandler<T>.update(resource: Resource<*>, resourceDiff: ResourceHandler.ResourceDiff<*>) {
    update(resource as Resource<T>, resourceDiff as ResourceDiff<T>)
  }
  // end type coercing extensions

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
