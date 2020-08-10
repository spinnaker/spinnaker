package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.UnsupportedKind
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.extensionsOf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Used to identify the [ResourceSpec] implementation that belongs with a [ResourceKind] when doing
 * things like reading resources from the database, or parsing JSON.
 */
@Component
class ResourceSpecIdentifier(
  private val kinds: List<SupportedKind<*>>,
  private val extensionRegistry: ExtensionRegistry? = null
) {
  @Autowired
  constructor(extensionRegistry: ExtensionRegistry) : this(emptyList(), extensionRegistry)

  private val ExtensionRegistry.supportedKinds: List<SupportedKind<*>>
    get() = extensionsOf<ResourceSpec>()
      .entries
      .map { SupportedKind(ResourceKind.parseKind(it.key), it.value) }

  fun identify(kind: ResourceKind): Class<out ResourceSpec> =
    // This gives priority to the extension registry which is auto-wired by default, and falls back on the list of
    // explicit supported kinds which is only used in tests.
    (extensionRegistry?.supportedKinds ?: kinds).find { it.kind == kind }?.specClass ?: throw UnsupportedKind(kind)

  /**
   * Constructor useful for tests so they can just wire in using varargs.
   */
  constructor(vararg kinds: SupportedKind<*>) : this(kinds.toList())
}
