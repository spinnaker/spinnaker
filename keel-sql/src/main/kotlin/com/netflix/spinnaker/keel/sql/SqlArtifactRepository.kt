package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.net.URI

class SqlArtifactRepository(
  private val jooq: DSLContext
) : ArtifactRepository {
  override fun register(artifact: DeliveryArtifact) {
    jooq.inTransaction {
      insertInto(DELIVERY_ARTIFACT, UID, NAME, TYPE)
        .values(randomUID().toString(), artifact.name, artifact.type.name)
        .execute()
    }
  }

  override fun store(artifactVersion: DeliveryArtifactVersion): Boolean =
    jooq.inTransaction {
      val uid = select(UID)
        .from(DELIVERY_ARTIFACT)
        .where(NAME.eq(artifactVersion.artifact.name))
        .and(TYPE.eq(artifactVersion.artifact.type.name))
        .fetchOne()

      requireNotNull(uid) {
        "No registered artifact with name ${artifactVersion.artifact.name} and type ${artifactVersion.artifact.type}"
      }

      insertInto(DELIVERY_ARTIFACT_VERSION, DELIVERY_ARTIFACT_UID, VERSION, PROVENANCE)
        .values(uid.value1(), artifactVersion.version, artifactVersion.provenance.toASCIIString())
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

  override fun versions(artifact: DeliveryArtifact): List<DeliveryArtifactVersion> =
    jooq
      .select(VERSION, PROVENANCE)
      .from(DELIVERY_ARTIFACT, DELIVERY_ARTIFACT_VERSION)
      .where(UID.eq(DELIVERY_ARTIFACT_UID))
      .and(NAME.eq(artifact.name))
      .and(TYPE.eq(artifact.type.name))
      .orderBy(VERSION.desc())
      .fetch()
      .map { (version, provenance) ->
        DeliveryArtifactVersion(artifact, version, URI.create(provenance))
      }

  companion object {
    private val DELIVERY_ARTIFACT = table("delivery_artifact")
    private val DELIVERY_ARTIFACT_VERSION = table("delivery_artifact_version")
    private val UID = field("uid", String::class.java)
    private val DELIVERY_ARTIFACT_UID = field("delivery_artifact_uid", String::class.java)
    private val NAME = field("name", String::class.java)
    private val TYPE = field("type", String::class.java)
    private val VERSION = field("version", String::class.java)
    private val PROVENANCE = field("provenance", String::class.java)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
