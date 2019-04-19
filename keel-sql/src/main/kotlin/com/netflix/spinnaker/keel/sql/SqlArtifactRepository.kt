package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.ResultQuery
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.net.URI

class SqlArtifactRepository(
  private val jooq: DSLContext
) : ArtifactRepository {
  override fun store(artifact: DeliveryArtifact) {
    jooq.inTransaction {
      insertInto(DELIVERY_ARTIFACT, NAME, TYPE)
        .values(artifact.name, artifact.type.name)
        .execute()
    }
  }

  override fun store(artifactVersion: DeliveryArtifactVersion) {
    jooq.inTransaction {
      val result = selectOne()
        .from(DELIVERY_ARTIFACT)
        .where(NAME.eq(artifactVersion.artifact.name))
        .and(TYPE.eq(artifactVersion.artifact.type.name))
        .fetchOne()
      if (result == null) {
        throw IllegalArgumentException()
      } else {
        insertInto(DELIVERY_ARTIFACT_VERSION, NAME, TYPE, VERSION, PROVENANCE)
          .values(
            artifactVersion.artifact.name,
            artifactVersion.artifact.type.name,
            artifactVersion.version,
            artifactVersion.provenance.toASCIIString()
          )
          .onDuplicateKeyIgnore()
          .execute()
      }
    }
  }

  override fun get(name: String, type: ArtifactType): DeliveryArtifact? =
    jooq
      .select(NAME, TYPE)
      .from(DELIVERY_ARTIFACT)
      .where(NAME.eq(name))
      .and(TYPE.eq(type.name))
      .fetchOne()
      .into<DeliveryArtifact>()

  override fun versions(artifact: DeliveryArtifact): List<DeliveryArtifactVersion> =
    jooq
      .select(NAME, TYPE, VERSION, PROVENANCE)
      .from(DELIVERY_ARTIFACT_VERSION)
      .where(NAME.eq(artifact.name))
      .and(TYPE.eq(artifact.type.name))
      .orderBy(VERSION.desc())
      .fetch()
      .map { (name, type, version, provenance) ->
        DeliveryArtifactVersion(
          DeliveryArtifact(
            name,
            ArtifactType.valueOf(type)
          ),
          version,
          URI.create(provenance)
        )
      }

  companion object {
    private val DELIVERY_ARTIFACT = table("delivery_artifact")
    private val DELIVERY_ARTIFACT_VERSION = table("delivery_artifact_version")
    private val NAME = field("name", String::class.java)
    private val TYPE = field("type", String::class.java)
    private val VERSION = field("version", String::class.java)
    private val PROVENANCE = field("provenance", String::class.java)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

private inline fun <reified E> Record.into(): E = into(E::class.java)

private inline fun <reified E> ResultQuery<*>.fetchInto(): List<E> = fetchInto(E::class.java)
