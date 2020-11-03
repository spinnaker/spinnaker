package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.resources.migrate

/**
 * This just unifies some logic that both [SqlResourceRepository] and [SqlDeliveryConfigRepository]
 * need in order to construct resources and auto-migrate their specs to the latest version.
 *
 * Ideally I'd just make this a package-level function but since it requires collaborators it's
 * easiest as a standalone class.
 *
 * Tests should test the behavior of the repository methods that use this class, rather than testing
 * this class directly. This is just an implementation detail.
 */
internal class ResourceFactory(
  private val mapper: ObjectMapper,
  private val resourceSpecIdentifier: ResourceSpecIdentifier,
  private val specMigrators: List<SpecMigrator<*, *>>
) : (String, String, String) -> Resource<*> {
  override fun invoke(kind: String, metadataJson: String, specJson: String) =
    Resource(
      ResourceKind.parseKind(kind),
      mapper.readValue<Map<String, Any?>>(metadataJson).asResourceMetadata(),
      mapper.readValue(specJson, resourceSpecIdentifier.identify(ResourceKind.parseKind(kind)))
    ).let { resource ->
      specMigrators
        .migrate(resource.kind, resource.spec)
        .let { (endKind, endSpec) ->
          Resource(endKind, resource.metadata, endSpec)
        }
    }
}
