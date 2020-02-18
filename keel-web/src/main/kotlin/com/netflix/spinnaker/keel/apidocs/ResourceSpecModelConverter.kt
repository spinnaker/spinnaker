package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component

/**
 * Adds [Schema]s for the available sub-types of [ResourceSpec]. This cannot be done with
 * annotations on [ResourceSpec] as the sub-types are not known at compile time.
 */
@Component
class ResourceSpecModelConverter(
  handlers: List<ResourceHandler<*, *>>
) : SubtypesModelConverter<ResourceSpec>(ResourceSpec::class.java) {

  override val subTypes = handlers.map { it.supportedKind.specClass }
}
