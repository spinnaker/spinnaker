package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.plugins.UnsupportedKind
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Used to identify the [ResourceSpec] implementation that belongs with a [ResourceKind] when doing
 * things like reading resources from the database, or parsing JSON.
 */
@Component
class ResourceSpecIdentifier @Autowired constructor(
  private val kinds: List<SupportedKind<*>>
) {
  fun identify(kind: ResourceKind): Class<out ResourceSpec> =
    kinds.find { it.kind == kind }?.specClass ?: throw UnsupportedKind(kind)

  /**
   * Constructor useful for tests so they can just wire in using varargs.
   */
  constructor(vararg kinds: SupportedKind<*>) : this(kinds.toList())
}
