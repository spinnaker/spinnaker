/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.netflix.spinnaker.clouddriver.sql.entitytags

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider
import com.netflix.spinnaker.clouddriver.sql.read
import com.netflix.spinnaker.clouddriver.sql.transactional
import com.netflix.spinnaker.kork.sql.routing.withPool
import java.time.Clock
import java.util.Optional
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.and
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory

/**
 * SQL-backed replacement for ElasticSearchEntityTagsProvider. Owns durable storage of entity tags
 * in the clouddriver database. `body` holds the canonical JSON EntityTags document; projection
 * columns and `entity_tag_index` exist only to support filtered queries.
 */
class SqlEntityTagsProvider(
  private val jooq: DSLContext,
  private val objectMapper: ObjectMapper,
  private val clock: Clock,
  private val poolName: String,
  private val reconciler: SqlEntityTagsReconciler? = null
) : EntityTagsProvider {

  companion object {
    private val log = LoggerFactory.getLogger(SqlEntityTagsProvider::class.java)
    private val TAGS = table("entity_tags")
    private val INDEX = table("entity_tag_index")
    private val BODY = field("body", String::class.java)
    private const val CHUNK = 100
  }

  override fun getAll(
    cloudProvider: String?,
    application: String?,
    entityType: String?,
    entityIds: List<String>?,
    idPrefix: String?,
    account: String?,
    region: String?,
    namespace: String?,
    tags: Map<String, Any>?,
    maxResults: Int
  ): Collection<EntityTags> {
    val conditions = mutableListOf<Condition>()
    cloudProvider?.let { conditions.add(field("cloud_provider").eq(it)) }
    application?.let { conditions.add(field("application").eq(it)) }
    account?.let { conditions.add(field("account").eq(it)) }
    region?.let { conditions.add(field("region").eq(it)) }
    entityType?.let { conditions.add(field("entity_type").eq(it.lowercase())) }
    entityIds?.takeIf { it.isNotEmpty() }?.let { conditions.add(field("entity_id").`in`(it)) }
    idPrefix?.let { conditions.add(field("id", String::class.java).like("$it%")) }

    if (namespace != null || !tags.isNullOrEmpty()) {
      val idxConditions = mutableListOf<Condition>()
      namespace?.let { idxConditions.add(field("namespace").eq(it.lowercase())) }
      tags?.keys?.takeIf { it.isNotEmpty() }?.let { keys ->
        idxConditions.add(field("name").`in`(keys.map { it.lowercase() }))
      }
      conditions.add(
        field("id").`in`(select(field("entity_tags_id")).from(INDEX).where(and(idxConditions)))
      )
    }

    val whereClause: Condition = if (conditions.isEmpty()) org.jooq.impl.DSL.trueCondition() else and(conditions)

    val rows = withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(BODY).from(TAGS).where(whereClause).limit(maxResults).fetch()
      }
    }
    return rows.mapNotNull { deserialize(it.get(BODY)) }
  }

  override fun get(id: String): Optional<EntityTags> {
    val body = withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(BODY).from(TAGS).where(field("id", String::class.java).eq(id)).fetchOne(BODY)
      }
    }
    return Optional.ofNullable(body).map { deserialize(it) }
  }

  override fun get(id: String, tags: Map<String, Any>?): Optional<EntityTags> {
    val et = get(id).orElse(null) ?: return Optional.empty()
    if (tags.isNullOrEmpty()) return Optional.of(et)
    val names = tags.keys.map { it.lowercase() }.toSet()
    val hasAll = et.tags.map { it.name.lowercase() }.toSet().containsAll(names)
    return if (hasAll) Optional.of(et) else Optional.empty()
  }

  override fun index(entityTags: EntityTags) {
    bulkIndex(listOf(entityTags))
  }

  override fun bulkIndex(multipleEntityTags: Collection<EntityTags>) {
    if (multipleEntityTags.isEmpty()) return
    multipleEntityTags.chunked(CHUNK).forEach { chunk ->
      withPool(poolName) {
        jooq.transactional { ctx ->
          chunk.forEach { et -> upsertOne(ctx, et) }
        }
      }
    }
  }

  override fun verifyIndex(entityTags: EntityTags) {
    // SQL is read-your-write; no eventual-consistency polling needed.
  }

  override fun delete(id: String) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.deleteFrom(INDEX).where(field("entity_tags_id").eq(id)).execute()
        ctx.deleteFrom(TAGS).where(field("id", String::class.java).eq(id)).execute()
      }
    }
  }

  override fun bulkDelete(multipleEntityTags: Collection<EntityTags>) {
    if (multipleEntityTags.isEmpty()) return
    val ids = multipleEntityTags.map { it.id }
    withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.deleteFrom(INDEX).where(field("entity_tags_id").`in`(ids)).execute()
        ctx.deleteFrom(TAGS).where(field("id", String::class.java).`in`(ids)).execute()
      }
    }
  }

  override fun deleteByNamespace(namespace: String, dryRun: Boolean, deleteFromSource: Boolean): Map<String, Any> {
    val ids = withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(field("entity_tags_id"))
          .from(INDEX)
          .where(field("namespace").eq(namespace))
          .fetch(field("entity_tags_id"), String::class.java)
      }
    }
    val result = mutableMapOf<String, Any>("namespace" to namespace, "matched" to ids.size, "dryRun" to dryRun)
    if (!dryRun && ids.isNotEmpty()) {
      // deleteFromSource is legacy: with a single store, delete is always authoritative.
      withPool(poolName) {
        jooq.transactional { ctx ->
          ctx.deleteFrom(INDEX).where(field("entity_tags_id").`in`(ids)).execute()
          ctx.deleteFrom(TAGS).where(field("id", String::class.java).`in`(ids)).execute()
        }
      }
      result["deleted"] = ids.size
    }
    return result
  }

  override fun deleteByTag(tag: String, dryRun: Boolean, deleteFromSource: Boolean): Map<String, Any> {
    val ids = withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(field("entity_tags_id"))
          .from(INDEX)
          .where(field("name").eq(tag))
          .fetch(field("entity_tags_id"), String::class.java)
      }
    }
    val result = mutableMapOf<String, Any>("tag" to tag, "matched" to ids.size, "dryRun" to dryRun)
    if (!dryRun && ids.isNotEmpty()) {
      withPool(poolName) {
        jooq.transactional { ctx ->
          ctx.deleteFrom(INDEX).where(field("entity_tags_id").`in`(ids)).execute()
          ctx.deleteFrom(TAGS).where(field("id", String::class.java).`in`(ids)).execute()
        }
      }
      result["deleted"] = ids.size
    }
    return result
  }

  override fun reindex() {
    // No-op: SQL is the source of truth. Retained for interface compatibility;
    // will be removed from the interface in the cleanup PR.
    log.info("reindex() is a no-op for SqlEntityTagsProvider; SQL is authoritative")
  }

  override fun delta(): Map<*, *> {
    val count = withPool(poolName) {
      jooq.read { ctx ->
        ctx.selectCount().from(TAGS).fetchOne(0, Int::class.java) ?: 0
      }
    }
    return mapOf("sql" to count)
  }

  override fun reconcile(cloudProvider: String, account: String, region: String, dryRun: Boolean): Map<*, *> {
    return reconciler?.reconcile(this, cloudProvider, account, region, dryRun)
      ?: mapOf("dryRun" to dryRun, "orphanCount" to 0, "skipped" to "reconciler not configured")
  }

  private fun upsertOne(ctx: DSLContext, et: EntityTags) {
    val now = clock.millis()
    val body = objectMapper.writeValueAsString(et)
    val ref = et.entityRef
    val cloudProvider: String? = ref?.cloudProvider
    val application: String? = ref?.application
    val account: String? = ref?.accountId ?: ref?.account
    val region: String? = ref?.region
    val entityType: String? = ref?.entityType
    val entityId: String? = ref?.entityId
    val lastModifiedBy = et.lastModifiedBy ?: "anonymous"

    ctx.insertInto(TAGS)
      .set(field("id"), et.id)
      .set(field("body"), body)
      .set(field("cloud_provider"), cloudProvider)
      .set(field("application"), application)
      .set(field("account"), account)
      .set(field("region"), region)
      .set(field("entity_type"), entityType)
      .set(field("entity_id"), entityId)
      .set(field("created_at"), et.lastModified ?: now)
      .set(field("last_modified_at"), et.lastModified ?: now)
      .set(field("last_modified_by"), lastModifiedBy)
      .onConflict(field("id"))
      .doUpdate()
      .set(field("body"), body)
      .set(field("cloud_provider"), cloudProvider)
      .set(field("application"), application)
      .set(field("account"), account)
      .set(field("region"), region)
      .set(field("entity_type"), entityType)
      .set(field("entity_id"), entityId)
      .set(field("last_modified_at"), et.lastModified ?: now)
      .set(field("last_modified_by"), lastModifiedBy)
      .execute()

    ctx.deleteFrom(INDEX).where(field("entity_tags_id").eq(et.id)).execute()
    et.tags?.forEach { tag ->
      // EntityTag getters already normalize to lowercase; index rows follow suit.
      ctx.insertInto(INDEX)
        .set(field("entity_tags_id"), et.id)
        .set(field("namespace"), tag.namespace)
        .set(field("name"), tag.name)
        .set(field("category"), tag.category)
        .execute()
    }
  }

  private fun deserialize(body: String?): EntityTags? {
    if (body == null) return null
    return try {
      objectMapper.readValue(body, EntityTags::class.java)
    } catch (e: Exception) {
      log.warn("failed to deserialize entity_tags row", e)
      null
    }
  }
}
