package com.netflix.spinnaker.keel.plugin.monitoring

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceError
import com.netflix.spinnaker.keel.plugin.ResourceMissing
import com.netflix.spinnaker.keel.plugin.ResourcePlugin
import com.netflix.spinnaker.keel.plugin.ResourceState
import org.javers.core.JaversBuilder
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ResourceStateMonitor(
  private val resourceRepository: ResourceRepository,
  private val plugins: List<ResourcePlugin>
) {

  @Scheduled(fixedDelayString = "\${keel.resource.monitoring.frequency.ms:60000}")
  fun validateManagedResources() {
    resourceRepository.allResources { (uid, apiVersion, singular) ->
      val plugin = pluginFor(apiVersion, singular) ?: throw UnsupportedKind(apiVersion, singular)
      val type = plugin.typeFor(singular)
      val resource = resourceRepository.get(uid, type)
      when (val response = plugin.current(resource)) {
        is ResourceMissing -> {
          log.warn("Resource {} \"{}\" is missing", resource.kind)
          plugin.create(resource)
        }
        is ResourceState<*> -> {
          val diff = resource.diff(response.spec)
          if (diff.hasChanges()) {
            log.warn("Resource {} \"{}\" is invalid", resource.kind, resource.metadata.name)
            log.info("Resource {} \"{}\" delta: {}", resource.kind, resource.metadata.name, diff)
            plugin.update(resource)
          } else {
            log.info("Resource {} \"{}\" is valid", resource.kind, resource.metadata.name)
          }
        }
        is ResourceError ->
          log.error(
            "Resource {} \"{}\" current state could not be determined due to \"{}\"",
            resource.kind,
            resource.metadata.name,
            response.reason
          )
      }
    }
  }

  private fun ResourcePlugin.typeFor(singular: String): Class<out Any> =
    supportedKinds.entries.find { it.key.singular == singular }?.value
      ?: throw IllegalArgumentException("Plugin $name does not support $singular")

  private val javers = JaversBuilder.javers().build()

  private fun Resource<*>.diff(current: Any) =
    javers.compare(spec, current)

  private fun pluginFor(apiVersion: ApiVersion, singular: String): ResourcePlugin? =
    plugins.find { it.apiVersion == apiVersion && it.supportedKinds.keys.map(ResourceKind::singular).contains(singular) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

class UnsupportedKind(apiVersion: ApiVersion, kind: String) : IllegalStateException("No plugin supporting \"$kind\" in \"$apiVersion\" is available")
