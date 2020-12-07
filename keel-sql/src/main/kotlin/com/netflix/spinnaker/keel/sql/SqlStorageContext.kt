package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import org.jooq.DSLContext
import java.time.Clock

abstract class SqlStorageContext(
  internal val jooq: DSLContext,
  internal val clock: Clock,
  internal val sqlRetry: SqlRetry,
  internal val objectMapper: ObjectMapper,
  internal val resourceSpecIdentifier: ResourceSpecIdentifier,
  internal val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  internal val specMigrators: List<SpecMigrator<*, *>>
) {
  internal val resourceFactory = ResourceFactory(objectMapper, resourceSpecIdentifier, specMigrators)
}
