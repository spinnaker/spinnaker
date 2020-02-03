package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoSuchDeliveryConfigName
import com.netflix.spinnaker.keel.persistence.OrphanedResourceException
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.CURRENT_CONSTRAINT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_ARTIFACT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.DELIVERY_CONFIG_LAST_CHECKED
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_CONSTRAINT
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.ENVIRONMENT_RESOURCE
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.RESOURCE
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Instant.EPOCH
import java.time.ZoneOffset
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.select
import org.jooq.util.mysql.MySQLDSL

class SqlDeliveryConfigRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val resourceTypeIdentifier: ResourceTypeIdentifier,
  private val mapper: ObjectMapper,
  private val sqlRetry: SqlRetry
) : DeliveryConfigRepository {

  override fun getByApplication(application: String): Collection<DeliveryConfig> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          DELIVERY_CONFIG.UID,
          DELIVERY_CONFIG.NAME,
          DELIVERY_CONFIG.APPLICATION,
          DELIVERY_CONFIG.SERVICE_ACCOUNT
        )
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.APPLICATION.eq(application))
        .fetch { (uid, name, application, serviceAccount) ->
          DeliveryConfig(name, application, serviceAccount).attachDependents(uid)
        }
    }

  override fun deleteByApplication(application: String): Int {

    val deliveryConfigUIDs = getUIDsByApplication(application)
    val environmentUIDs = getEnvironmentUIDs(deliveryConfigUIDs)

    deliveryConfigUIDs.forEach { uid ->
      sqlRetry.withRetry(WRITE) {
        jooq.deleteFrom(DELIVERY_CONFIG)
          .where(DELIVERY_CONFIG.UID.eq(uid))
          .execute()
      }
      sqlRetry.withRetry(WRITE) {
        jooq.deleteFrom(DELIVERY_CONFIG_ARTIFACT)
          .where(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID.eq(uid))
          .execute()
      }
      sqlRetry.withRetry(WRITE) {
        jooq.deleteFrom(DELIVERY_CONFIG_LAST_CHECKED)
          .where(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID.eq(uid))
          .execute()
      }
    }
    environmentUIDs.forEach { uid ->
      sqlRetry.withRetry(WRITE) {
        jooq.deleteFrom(ENVIRONMENT)
          .where(ENVIRONMENT.UID.eq(uid))
          .execute()
      }
      sqlRetry.withRetry(WRITE) {
        jooq.deleteFrom(ENVIRONMENT_ARTIFACT_VERSIONS)
          .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(uid))
          .execute()
      }
      sqlRetry.withRetry(WRITE) {
        jooq.deleteFrom(ENVIRONMENT_RESOURCE)
          .where(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(uid))
          .execute()
      }
    }
    return deliveryConfigUIDs.size
  }

  override fun delete(name: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)
        val deliveryConfigUid: String = txn
          .select(DELIVERY_CONFIG.UID)
          .from(DELIVERY_CONFIG)
          .where(DELIVERY_CONFIG.NAME.eq(name))
          .fetchOne(DELIVERY_CONFIG.UID)
        txn
          .deleteFrom(DELIVERY_CONFIG)
          .where(DELIVERY_CONFIG.NAME.eq(name))
          .execute()
        txn
          .deleteFrom(DELIVERY_CONFIG_LAST_CHECKED)
          .where(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID.eq(deliveryConfigUid))
          .execute()
      }
    }
  }

  override fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(ENVIRONMENT_RESOURCE)
        .where(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(envUid(deliveryConfigName, environmentName)))
        .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(resourceId.uid))
        .execute()
    }
  }

  override fun deleteEnvironment(deliveryConfigName: String, environmentName: String) {
    val deliveryConfigUid = sqlRetry.withRetry(READ) {
      jooq.select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(deliveryConfigName))
        .fetchOne(DELIVERY_CONFIG.UID)
    }
    val envUid = envUid(deliveryConfigName, environmentName)
    sqlRetry.withRetry(WRITE) {
      jooq.transaction { config ->
        val txn = DSL.using(config)
        txn
          .deleteFrom(ENVIRONMENT_ARTIFACT_CONSTRAINT)
          .where(ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(envUid))
          .execute()
        txn
          .deleteFrom(ENVIRONMENT_ARTIFACT_VERSIONS)
          .where(ENVIRONMENT_ARTIFACT_VERSIONS.ENVIRONMENT_UID.eq(envUid))
          .execute()
        txn
          .deleteFrom(ENVIRONMENT_RESOURCE)
          .where(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(envUid))
          .execute()
        txn
          .deleteFrom(ENVIRONMENT)
          .where(ENVIRONMENT.UID.eq(envUid))
          .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(deliveryConfigUid))
          .execute()
      }
    }
  }

  private fun getUIDsByApplication(application: String): List<String> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.APPLICATION.eq(application))
        .fetch(DELIVERY_CONFIG.UID)
    }

  private fun getEnvironmentUIDs(deliveryConfigUIDs: List<String>): List<String> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(ENVIRONMENT.UID)
        .from(ENVIRONMENT)
        .where(ENVIRONMENT.DELIVERY_CONFIG_UID.`in`(deliveryConfigUIDs))
        .fetch(ENVIRONMENT.UID)
    }

  // todo: queries in this function aren't inherently retryable because of the cross-repository interactions
  // from where this is called: https://github.com/spinnaker/keel/issues/740
  override fun store(deliveryConfig: DeliveryConfig) {
    with(deliveryConfig) {
      val uid = jooq
        .select(DELIVERY_CONFIG.UID)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(name))
        .fetchOne(DELIVERY_CONFIG.UID)
        ?: randomUID().toString()
      jooq.insertInto(DELIVERY_CONFIG)
        .set(DELIVERY_CONFIG.UID, uid)
        .set(DELIVERY_CONFIG.NAME, name)
        .set(DELIVERY_CONFIG.APPLICATION, application)
        .set(DELIVERY_CONFIG.SERVICE_ACCOUNT, serviceAccount)
        .onDuplicateKeyIgnore()
        .execute()
      artifacts.forEach { artifact ->
        jooq.insertInto(DELIVERY_CONFIG_ARTIFACT)
          .set(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID, uid)
          .set(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID, jooq
            .select(DELIVERY_ARTIFACT.UID)
            .from(DELIVERY_ARTIFACT)
            .where(DELIVERY_ARTIFACT.NAME.eq(artifact.name))
            .and(DELIVERY_ARTIFACT.TYPE.eq(artifact.type.name))
            .and(DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME.eq(artifact.deliveryConfigName))
            .and(DELIVERY_ARTIFACT.REFERENCE.eq(artifact.reference)))
          .onDuplicateKeyIgnore()
          .execute()
      }
      environments.forEach { environment ->
        val environmentUID = (
          jooq
            .select(ENVIRONMENT.UID)
            .from(ENVIRONMENT)
            .where(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
            .and(ENVIRONMENT.NAME.eq(environment.name))
            .fetchOne(ENVIRONMENT.UID)
            ?: randomUID().toString()
          )
          .also {
            jooq.insertInto(ENVIRONMENT)
              .set(ENVIRONMENT.UID, it)
              .set(ENVIRONMENT.DELIVERY_CONFIG_UID, uid)
              .set(ENVIRONMENT.NAME, environment.name)
              .set(ENVIRONMENT.CONSTRAINTS, mapper.writeValueAsString(environment.constraints))
              .set(ENVIRONMENT.NOTIFICATIONS, mapper.writeValueAsString(environment.notifications))
              .onDuplicateKeyUpdate()
              .set(ENVIRONMENT.CONSTRAINTS, mapper.writeValueAsString(environment.constraints))
              .set(ENVIRONMENT.NOTIFICATIONS, mapper.writeValueAsString(environment.notifications))
              .execute()
          }
        environment.resources.forEach { resource ->
          jooq.insertInto(ENVIRONMENT_RESOURCE)
            .set(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID, environmentUID)
            .set(ENVIRONMENT_RESOURCE.RESOURCE_UID, select(RESOURCE.UID).from(RESOURCE).where(RESOURCE.ID.eq(resource.id)))
            .onDuplicateKeyIgnore()
            .execute()
        }
      }
      jooq.insertInto(DELIVERY_CONFIG_LAST_CHECKED)
        .set(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID, uid)
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
        .onDuplicateKeyUpdate()
        .set(DELIVERY_CONFIG_LAST_CHECKED.AT, EPOCH.plusSeconds(1).toLocal())
        .execute()
    }
  }

  override fun get(name: String): DeliveryConfig =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          DELIVERY_CONFIG.UID,
          DELIVERY_CONFIG.NAME,
          DELIVERY_CONFIG.APPLICATION,
          DELIVERY_CONFIG.SERVICE_ACCOUNT
        )
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(name))
        .fetchOne { (uid, name, application, serviceAccount) ->
          uid to DeliveryConfig(name, application, serviceAccount)
        }
    }
      ?.let { (uid, deliveryConfig) ->
        deliveryConfig.attachDependents(uid)
      }
      ?: throw NoSuchDeliveryConfigName(name)

  private fun DeliveryConfig.attachDependents(uid: String): DeliveryConfig =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_ARTIFACT.NAME, DELIVERY_ARTIFACT.TYPE, DELIVERY_ARTIFACT.DETAILS, DELIVERY_ARTIFACT.REFERENCE, DELIVERY_ARTIFACT.DELIVERY_CONFIG_NAME)
        .from(DELIVERY_ARTIFACT, DELIVERY_CONFIG_ARTIFACT)
        .where(DELIVERY_CONFIG_ARTIFACT.ARTIFACT_UID.eq(DELIVERY_ARTIFACT.UID))
        .and(DELIVERY_CONFIG_ARTIFACT.DELIVERY_CONFIG_UID.eq(uid))
        .fetch { (name, type, details, reference, configName) ->
          mapToArtifact(name, ArtifactType.valueOf(type), details, reference, configName)
        }
    }
      .toSet()
      .let { artifacts ->
        sqlRetry.withRetry(READ) {
          jooq
            .select(ENVIRONMENT.UID, ENVIRONMENT.NAME, ENVIRONMENT.CONSTRAINTS, ENVIRONMENT.NOTIFICATIONS)
            .from(ENVIRONMENT)
            .where(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(uid))
            .fetch { (environmentUid, name, constraintsJson, notificationsJson) ->
              Environment(
                name = name,
                resources = resourcesForEnvironment(environmentUid),
                constraints = mapper.readValue(constraintsJson),
                notifications = mapper.readValue(notificationsJson ?: "[]")
              )
            }
            .let { environments ->
              copy(
                artifacts = artifacts,
                environments = environments.toSet()
              )
            }
        }
      }

  override fun environmentFor(resourceId: String): Environment =
    sqlRetry.withRetry(READ) {
      jooq
        .select(ENVIRONMENT.UID, ENVIRONMENT.NAME, ENVIRONMENT.CONSTRAINTS, ENVIRONMENT.NOTIFICATIONS)
        .from(ENVIRONMENT, ENVIRONMENT_RESOURCE, RESOURCE)
        .where(RESOURCE.ID.eq(resourceId))
        .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
        .fetchOne { (uid, name, constraintsJson, notificationsJson) ->
          Environment(
            name = name,
            resources = resourcesForEnvironment(uid),
            constraints = mapper.readValue(constraintsJson),
            notifications = mapper.readValue(notificationsJson ?: "[]")
          )
        }
    } ?: throw OrphanedResourceException(resourceId)

  override fun deliveryConfigFor(resourceId: String): DeliveryConfig =
    // TODO: this implementation could be more efficient by sharing code with get(name)
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.NAME)
        .from(ENVIRONMENT, ENVIRONMENT_RESOURCE, RESOURCE, DELIVERY_CONFIG)
        .where(RESOURCE.ID.eq(resourceId))
        .and(ENVIRONMENT_RESOURCE.RESOURCE_UID.eq(RESOURCE.UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(ENVIRONMENT.UID))
        .and(ENVIRONMENT.DELIVERY_CONFIG_UID.eq(DELIVERY_CONFIG.UID))
        .fetchOne { (name) ->
          get(name)
        }
    } ?: throw OrphanedResourceException(resourceId)

  override fun storeConstraintState(state: ConstraintState) {
    environmentUidByName(state.deliveryConfigName, state.environmentName)
      ?.also { envUid ->
        val uid = sqlRetry.withRetry(READ) {
          jooq
            .select(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID)
            .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
            .where(
              ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(envUid),
              ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE.eq(state.type),
              ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION.eq(state.artifactVersion)
            )
            .fetchOne(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID)
        } ?: randomUID().toString()

        val application = applicationByDeliveryConfigName(state.deliveryConfigName)

        sqlRetry.withRetry(WRITE) {
          jooq.transaction { config ->
            val txn = DSL.using(config)
            txn
              .insertInto(ENVIRONMENT_ARTIFACT_CONSTRAINT)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID, uid)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID, envUid)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION, state.artifactVersion)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE, state.type)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT, state.createdAt.toLocal())
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS, state.status.toString())
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY, state.judgedBy)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT, state.judgedAt?.toLocal())
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT, state.comment)
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES, mapper.writeValueAsString(state.attributes))
              .onDuplicateKeyUpdate()
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS, MySQLDSL.values(ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS))
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY, MySQLDSL.values(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY))
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT, MySQLDSL.values(ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT))
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT, MySQLDSL.values(ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT))
              .set(ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES, MySQLDSL.values(ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES))
              .execute()

            txn
              .insertInto(CURRENT_CONSTRAINT)
              .set(CURRENT_CONSTRAINT.APPLICATION, application)
              .set(CURRENT_CONSTRAINT.ENVIRONMENT_UID, envUid)
              .set(CURRENT_CONSTRAINT.TYPE, state.type)
              .set(CURRENT_CONSTRAINT.CONSTRAINT_UID, uid)
              .onDuplicateKeyUpdate()
              .set(CURRENT_CONSTRAINT.CONSTRAINT_UID, MySQLDSL.values(CURRENT_CONSTRAINT.CONSTRAINT_UID))
              .execute()
          }
        }
      }
  }

  override fun getConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    type: String
  ): ConstraintState? {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return null
    return sqlRetry.withRetry(READ) {
      jooq
        .select(
          inline(deliveryConfigName).`as`("deliveryConfigName"),
          inline(environmentName).`as`("environmentName"),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .where(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(environmentUID),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION.eq(artifactVersion),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE.eq(type)
        )
        .fetchOne { (deliveryConfigName,
                      environmentName,
                      artifactVersion,
                      constraintType,
                      status,
                      createdAt,
                      judgedBy,
                      judgedAt,
                      comment,
                      attributes) ->
          ConstraintState(
            deliveryConfigName,
            environmentName,
            artifactVersion,
            constraintType,
            ConstraintStatus.valueOf(status),
            createdAt.toInstant(ZoneOffset.UTC),
            judgedBy,
            when (judgedAt) {
              null -> null
              else -> judgedAt.toInstant(ZoneOffset.UTC)
            },
            comment,
            mapper.readValue(attributes)
          )
        }
    }
  }

  override fun constraintStateFor(application: String): List<ConstraintState> {
    val environmentNames = mutableMapOf<String, String>()
    val deliveryConfigsByEnv = mutableMapOf<String, String>()
    val constraintResult = sqlRetry.withRetry(READ) {
      jooq
        .select(
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(CURRENT_CONSTRAINT)
        .innerJoin(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .on(CURRENT_CONSTRAINT.CONSTRAINT_UID.eq(ENVIRONMENT_ARTIFACT_CONSTRAINT.UID))
        .fetch()
    }

    if (constraintResult.isEmpty()) {
      return emptyList()
    }

    sqlRetry.withRetry(READ) {
      jooq
        .select(
          CURRENT_CONSTRAINT.ENVIRONMENT_UID,
          ENVIRONMENT.NAME,
          DELIVERY_CONFIG.NAME)
        .from(CURRENT_CONSTRAINT)
        .innerJoin(ENVIRONMENT)
        .on(ENVIRONMENT.UID.eq(CURRENT_CONSTRAINT.ENVIRONMENT_UID))
        .innerJoin(DELIVERY_CONFIG)
        .on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
        .where(CURRENT_CONSTRAINT.APPLICATION.eq(application))
        .fetch { (envId, envName, dcName) ->
          environmentNames[envId] = envName
          deliveryConfigsByEnv[envId] = dcName
        }
    }

    return constraintResult.map { (envId,
                                    artifactVersion,
                                    type,
                                    createdAt,
                                    status,
                                    judgedBy,
                                    judgedAt,
                                    comment,
                                    attributes) ->
      ConstraintState(
        deliveryConfigsByEnv[envId] ?: error("Environment id $envId does not belong to a delivery-config"),
        environmentNames[envId] ?: error("Invalid environment id $envId"),
        artifactVersion,
        type,
        ConstraintStatus.valueOf(status),
        createdAt.toInstant(ZoneOffset.UTC),
        judgedBy,
        when (judgedAt) {
          null -> null
          else -> judgedAt.toInstant(ZoneOffset.UTC)
        },
        comment,
        mapper.readValue(attributes)
      )
    }
  }

  override fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    limit: Int
  ): List<ConstraintState> {
    val environmentUID = environmentUidByName(deliveryConfigName, environmentName)
      ?: return emptyList()
    return sqlRetry.withRetry(READ) {
      jooq
        .select(
          inline(deliveryConfigName).`as`("deliveryConfigName"),
          inline(environmentName).`as`("environmentName"),
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ARTIFACT_VERSION,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.TYPE,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.STATUS,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_BY,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.JUDGED_AT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.COMMENT,
          ENVIRONMENT_ARTIFACT_CONSTRAINT.ATTRIBUTES
        )
        .from(ENVIRONMENT_ARTIFACT_CONSTRAINT)
        .where(ENVIRONMENT_ARTIFACT_CONSTRAINT.ENVIRONMENT_UID.eq(environmentUID))
        .orderBy(ENVIRONMENT_ARTIFACT_CONSTRAINT.CREATED_AT.desc())
        .limit(limit)
        .fetch { (deliveryConfigName,
                   environmentName,
                   artifactVersion,
                   constraintType,
                   status,
                   createdAt,
                   judgedBy,
                   judgedAt,
                   comment,
                   attributes) ->
          ConstraintState(
            deliveryConfigName,
            environmentName,
            artifactVersion,
            constraintType,
            ConstraintStatus.valueOf(status),
            createdAt.toInstant(ZoneOffset.UTC),
            judgedBy,
            when (judgedAt) {
              null -> null
              else -> judgedAt.toInstant(ZoneOffset.UTC)
            },
            comment,
            mapper.readValue(attributes)
          )
        }
    }
  }

  private fun resourcesForEnvironment(uid: String) =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          RESOURCE.API_VERSION,
          RESOURCE.KIND,
          RESOURCE.METADATA,
          RESOURCE.SPEC
        )
        .from(RESOURCE, ENVIRONMENT_RESOURCE)
        .where(RESOURCE.UID.eq(ENVIRONMENT_RESOURCE.RESOURCE_UID))
        .and(ENVIRONMENT_RESOURCE.ENVIRONMENT_UID.eq(uid))
        .fetch { (apiVersion, kind, metadata, spec) ->
          Resource(
            apiVersion,
            kind,
            mapper.readValue<Map<String, Any?>>(metadata).asResourceMetadata(),
            mapper.readValue(spec, resourceTypeIdentifier.identify(apiVersion, kind))
          )
        }
    }
      .toSet()

  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryConfig> {
    val now = clock.instant()
    val cutoff = now.minus(minTimeSinceLastCheck).toLocal()
    return sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(DELIVERY_CONFIG.UID, DELIVERY_CONFIG.NAME)
          .from(DELIVERY_CONFIG, DELIVERY_CONFIG_LAST_CHECKED)
          .where(DELIVERY_CONFIG.UID.eq(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID))
          .and(DELIVERY_CONFIG_LAST_CHECKED.AT.lessOrEqual(cutoff))
          .orderBy(DELIVERY_CONFIG_LAST_CHECKED.AT)
          .limit(limit)
          .forUpdate()
          .fetch()
          .also {
            it.forEach { (uid, _) ->
              insertInto(DELIVERY_CONFIG_LAST_CHECKED)
                .set(DELIVERY_CONFIG_LAST_CHECKED.DELIVERY_CONFIG_UID, uid)
                .set(DELIVERY_CONFIG_LAST_CHECKED.AT, now.toLocal())
                .onDuplicateKeyUpdate()
                .set(DELIVERY_CONFIG_LAST_CHECKED.AT, now.toLocal())
                .execute()
            }
          }
          .map { (_, name) ->
            get(name)
          }
      }
    }
  }

  private val String.uid: Select<Record1<String>>
    get() = select(RESOURCE.UID)
      .from(RESOURCE)
      .where(RESOURCE.ID.eq(this))

  private fun environmentUidByName(deliveryConfigName: String, environmentName: String): String? =
    sqlRetry.withRetry(READ) {
      jooq
        .select(ENVIRONMENT.UID)
        .from(DELIVERY_CONFIG)
        .innerJoin(ENVIRONMENT).on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
        .where(
          DELIVERY_CONFIG.NAME.eq(deliveryConfigName),
          ENVIRONMENT.NAME.eq(environmentName)
        )
        .fetchOne(ENVIRONMENT.UID)
    }
      ?: null

  private fun envUid(deliveryConfigName: String, environmentName: String): Select<Record1<String>> =
    select(ENVIRONMENT.UID)
      .from(DELIVERY_CONFIG)
      .innerJoin(ENVIRONMENT).on(DELIVERY_CONFIG.UID.eq(ENVIRONMENT.DELIVERY_CONFIG_UID))
      .where(
        DELIVERY_CONFIG.NAME.eq(deliveryConfigName),
        ENVIRONMENT.NAME.eq(environmentName)
      )

  private fun applicationByDeliveryConfigName(name: String): String =
    sqlRetry.withRetry(READ) {
      jooq
        .select(DELIVERY_CONFIG.APPLICATION)
        .from(DELIVERY_CONFIG)
        .where(DELIVERY_CONFIG.NAME.eq(name))
        .fetchOne(DELIVERY_CONFIG.APPLICATION)
    }
      ?: throw NoSuchDeliveryConfigName(name)

  private fun Instant.toLocal() = atZone(clock.zone).toLocalDateTime()
}
