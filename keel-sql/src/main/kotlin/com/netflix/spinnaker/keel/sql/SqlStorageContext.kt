package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import org.jooq.DSLContext
import java.time.Clock

/**
 * TODO: Docs
 */
abstract class SqlStorageContext(
  internal val jooq: DSLContext,
  internal val clock: Clock,
  internal val sqlRetry: SqlRetry,
  internal val objectMapper: ObjectMapper,
  internal val resourceFactory: ResourceFactory,
  internal val artifactSuppliers: List<ArtifactSupplier<*, *>>
)
