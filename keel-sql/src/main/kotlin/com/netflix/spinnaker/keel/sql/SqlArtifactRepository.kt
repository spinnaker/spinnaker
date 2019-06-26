package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import org.jooq.DSLContext
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory

class SqlArtifactRepository(
  private val jooq: DSLContext
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    jooq.inTransaction {
      insertInto(DELIVERY_ARTIFACT)
        .columns(UID, NAME, TYPE)
        .values(randomUID().toString(), artifact.name, artifact.type.name)
        .onDuplicateKeyIgnore()
        .execute()
        .also { count ->
          if (count == 0) throw ArtifactAlreadyRegistered(artifact)
        }
    }
  }

  override fun store(artifact: DeliveryArtifact, version: String): Boolean =
    jooq.inTransaction {
      val uid = select(UID)
        .from(DELIVERY_ARTIFACT)
        .where(NAME.eq(artifact.name))
        .and(TYPE.eq(artifact.type.name))
        .fetchOne()
        ?: throw NoSuchArtifactException(artifact)

      insertInto(DELIVERY_ARTIFACT_VERSION, DELIVERY_ARTIFACT_UID, VERSION)
        .values(uid.value1(), version)
        .onDuplicateKeyIgnore()
        .execute() == 1
    }

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    jooq
      .selectOne()
      .from(DELIVERY_ARTIFACT)
      .where(NAME.eq(name))
      .and(TYPE.eq(type.name))
      .fetchOne() != null

  override fun versions(artifact: DeliveryArtifact): List<String> =
    if (isRegistered(artifact.name, artifact.type)) {
      jooq
        .select(VERSION)
        .from(DELIVERY_ARTIFACT, DELIVERY_ARTIFACT_VERSION)
        .where(UID.eq(DELIVERY_ARTIFACT_UID))
        .and(NAME.eq(artifact.name))
        .and(TYPE.eq(artifact.type.name))
        .orderBy(VERSION.desc())
        .fetch()
        .getValues(VERSION)
    } else {
      throw NoSuchArtifactException(artifact)
    }

  companion object {
    private val DELIVERY_ARTIFACT = table("delivery_artifact")
    private val DELIVERY_ARTIFACT_VERSION = table("delivery_artifact_version")
    private val UID = field<String>("uid")
    private val DELIVERY_ARTIFACT_UID = field<String>("delivery_artifact_uid")
    private val NAME = field<String>("name")
    private val TYPE = field<String>("type")
    private val VERSION = field<String>("version")
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
