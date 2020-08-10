package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.UnsupportedKind
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.extensionsOf
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Used to identify the [ResourceSpec] implementation that belongs with a [ResourceKind] when doing
 * things like reading resources from the database, or parsing JSON.
 */
@Component
class ResourceSpecIdentifier(
  private val kinds: List<SupportedKind<*>>
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Autowired
  constructor(extensionRegistry: ExtensionRegistry) :
    this(extensionRegistry
      .extensionsOf<ResourceSpec>()
      .entries
      .map { SupportedKind(ResourceKind.parseKind(it.key), it.value) }
    ) {
    log.debug("Registered the following kinds from extension registry: ${kinds.map { it.kind }.joinToString()}")
  }

  fun identify(kind: ResourceKind): Class<out ResourceSpec> =
    kinds.find { it.kind == kind }?.specClass ?: throw UnsupportedKind(kind)

  /**
   * Constructor useful for tests so they can just wire in using varargs.
   */
  constructor(vararg kinds: SupportedKind<*>) : this(kinds.toList())
}
