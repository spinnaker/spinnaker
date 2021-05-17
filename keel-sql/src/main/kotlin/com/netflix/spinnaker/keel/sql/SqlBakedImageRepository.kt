package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.artifacts.BakedImage
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.BAKED_IMAGES
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.time.Clock

class SqlBakedImageRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val objectMapper: ObjectMapper,
  private val sqlRetry: SqlRetry,
) : BakedImageRepository {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun store(image: BakedImage) {
    log.debug("Storing baked image $image")
    sqlRetry.withRetry(WRITE) {
      jooq
        .insertInto(BAKED_IMAGES)
        .set(BAKED_IMAGES.IMAGE, objectMapper.writeValueAsString(image))
        .set(BAKED_IMAGES.TIME_DETECTED, clock.instant())
        .onDuplicateKeyUpdate()
        .set(BAKED_IMAGES.IMAGE, objectMapper.writeValueAsString(image))
        .set(BAKED_IMAGES.TIME_DETECTED, clock.instant())
        .execute()
    }
  }

  override fun getByArtifactVersion(version: String, artifact: DebianArtifact): BakedImage? {
     return sqlRetry.withRetry(READ) {
      jooq
        .select(BAKED_IMAGES.IMAGE)
        .from(BAKED_IMAGES)
        .where(BAKED_IMAGES.PACKAGE_VERSION.eq(version))
        .and(BAKED_IMAGES.CLOUD_PROVIDER.eq("aws"))
        .and(BAKED_IMAGES.BASE_OS.eq(artifact.vmOptions.baseOs))
        .and(BAKED_IMAGES.BASE_LABEL.eq(artifact.vmOptions.baseLabel.name))
        .fetch { (image) ->
          objectMapper.readValue(image, BakedImage::class.java)
        }
        .firstOrNull()
    }
  }
}
