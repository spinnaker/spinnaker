package com.netflix.spinnaker.keel.annealing

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Missing
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import de.danielbechler.diff.ObjectDifferBuilder
import de.danielbechler.diff.node.DiffNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ResourceActuator(
  private val resourceRepository: ResourceRepository,
  private val handlers: List<ResourceHandler<*>>
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
          log.warn("Resource {} is missing", resource.metadata.name)
          resourceRepository.updateState(resource.metadata.uid, Missing)
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
            log.warn("Resource {} is invalid", resource.metadata.name)
            log.info("Resource {} delta: {}", resource.metadata.name, builder.toString())
            resourceRepository.updateState(resource.metadata.uid, Diff)
            plugin.update(resource, diff)
          } else {
            log.info("Resource {} is valid", resource.metadata.name)
            resourceRepository.updateState(resource.metadata.uid, Ok)
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
  private fun <T : Any> ResourceHandler<T>.update(resource: Resource<*>, diff: DiffNode) {
    update(resource as Resource<T>, diff)
  }
  // end type coercing extensions

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
