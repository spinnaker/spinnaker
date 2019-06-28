package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT_VERSION
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

class SqlArtifactRepository(
  private val jooq: DSLContext
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    jooq.inTransaction {
      insertInto(DELIVERY_ARTIFACT)
        .set(DELIVERY_ARTIFACT.UID, randomUID().toString())
        .set(DELIVERY_ARTIFACT.NAME, artifact.name)
        .set(DELIVERY_ARTIFACT.TYPE, artifact.type.name)
        .onDuplicateKeyIgnore()
        .execute()
        .also { count ->
          if (count == 0) throw ArtifactAlreadyRegistered(artifact)
        }
    }
  }

  override fun store(artifact: DeliveryArtifact, version: String): Boolean =
    jooq.inTransaction {
      val uid = select(DELIVERY_ARTIFACT.UID)
        .from(DELIVERY_ARTIFACT)
        .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
        .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
        .fetchOne()
        ?: throw NoSuchArtifactException(artifact)

      insertInto(DELIVERY_ARTIFACT_VERSION)
        .set(DELIVERY_ARTIFACT_VERSION.DELIVERY_ARTIFACT_UID, uid.value1())
        .set(DELIVERY_ARTIFACT_VERSION.VERSION, version)
        .onDuplicateKeyIgnore()
        .execute() == 1
    }

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    jooq
      .selectOne()
      .from(DELIVERY_ARTIFACT)
      .where(DELIVERY_ARTIFACT.NAME.eq(name))
      .and(DELIVERY_ARTIFACT.TYPE.eq(type.name))
      .fetchOne() != null

  override fun versions(artifact: DeliveryArtifact): List<String> =
    if (isRegistered(artifact.name, artifact.type)) {
      jooq
        .select(DELIVERY_ARTIFACT_VERSION.VERSION)
        .from(DELIVERY_ARTIFACT, DELIVERY_ARTIFACT_VERSION)
        .where(DELIVERY_ARTIFACT.UID.eq(DELIVERY_ARTIFACT_VERSION.DELIVERY_ARTIFACT_UID))
        .and(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
        .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
        .orderBy(DELIVERY_ARTIFACT_VERSION.VERSION.desc())
        .fetch()
        .getValues(DELIVERY_ARTIFACT_VERSION.VERSION)
    } else {
      throw NoSuchArtifactException(artifact)
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
