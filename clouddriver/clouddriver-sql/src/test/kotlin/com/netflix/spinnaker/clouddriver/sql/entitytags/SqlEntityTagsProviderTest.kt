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
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlEntityTagsProviderTest {

  private val database = SqlTestUtil.initTcMysqlDatabase()!!
  private val objectMapper = ObjectMapper()
  private val subject = SqlEntityTagsProvider(
    jooq = database.context,
    objectMapper = objectMapper,
    clock = Clock.systemDefaultZone(),
    poolName = "default"
  )

  @AfterEach
  fun cleanup() {
    database.context.deleteFrom(table("entity_tag_index")).execute()
    database.context.deleteFrom(table("entity_tags")).execute()
  }

  private fun tag(
    id: String,
    cloudProvider: String = "aws",
    application: String = "spinnaker",
    account: String = "prod",
    region: String = "us-east-1",
    entityType: String = "servergroup",
    entityId: String = "spinnaker-v001",
    tags: List<Pair<String, String>> = listOf("stack" to "prod")
  ): EntityTags {
    val et = EntityTags()
    et.id = id
    et.lastModified = 1000L
    et.lastModifiedBy = "tester"
    val ref = EntityTags.EntityRef()
    ref.cloudProvider = cloudProvider
    ref.application = application
    ref.account = account
    ref.region = region
    ref.entityType = entityType
    ref.entityId = entityId
    et.entityRef = ref
    et.tags = tags.map { (name, ns) ->
      EntityTags.EntityTag().also {
        it.name = name
        it.namespace = ns
        it.value = "v"
        it.valueType = EntityTags.EntityTagValueType.literal
      }
    }
    return et
  }

  @Test
  fun `index and get single tag`() {
    val et = tag("aws:servergroup:spinnaker-v001:prod:us-east-1")
    subject.index(et)

    val fetched = subject.get(et.id)
    expectThat(fetched.isPresent).isTrue()
    expectThat(fetched.get().id).isEqualTo(et.id)
    expectThat(fetched.get().tags).hasSize(1)
  }

  @Test
  fun `bulkIndex writes multiple rows and rebuilds tag index`() {
    val a = tag("aws:servergroup:a-v001:prod:us-east-1", entityId = "a-v001")
    val b = tag("aws:servergroup:b-v001:prod:us-east-1", entityId = "b-v001",
      tags = listOf("stack" to "prod", "team" to "prod"))
    subject.bulkIndex(listOf(a, b))

    expectThat(database.context.fetchCount(table("entity_tags"))).isEqualTo(2)
    expectThat(database.context.fetchCount(table("entity_tag_index"))).isEqualTo(3)
  }

  @Test
  fun `getAll filters by application`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1", application = "one"))
    subject.index(tag("aws:servergroup:two:prod:us-east-1", application = "two"))

    val results = subject.getAll(null, "one", null, null, null, null, null, null, null, 100)
    expectThat(results.map { it.id }).containsExactlyInAnyOrder("aws:servergroup:one:prod:us-east-1")
  }

  @Test
  fun `getAll filters by cloudProvider account region`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1", account = "prod", region = "us-east-1"))
    subject.index(tag("aws:servergroup:two:test:us-east-1", account = "test", region = "us-east-1"))
    subject.index(tag("aws:servergroup:three:prod:us-west-2", account = "prod", region = "us-west-2"))

    val results = subject.getAll("aws", null, null, null, null, "prod", "us-east-1", null, null, 100)
    expectThat(results).hasSize(1)
    expectThat(results.first().id).isEqualTo("aws:servergroup:one:prod:us-east-1")
  }

  @Test
  fun `getAll filters by entityType and entityIds`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1", entityType = "servergroup", entityId = "one"))
    subject.index(tag("aws:loadbalancer:lb-1:prod:us-east-1", entityType = "loadbalancer", entityId = "lb-1"))

    val servergroups = subject.getAll(null, null, "servergroup", null, null, null, null, null, null, 100)
    expectThat(servergroups.map { it.entityRef.entityId }).containsExactlyInAnyOrder("one")

    val byIds = subject.getAll(null, null, null, listOf("lb-1"), null, null, null, null, null, 100)
    expectThat(byIds).hasSize(1)
    expectThat(byIds.first().id).isEqualTo("aws:loadbalancer:lb-1:prod:us-east-1")
  }

  @Test
  fun `getAll filters by idPrefix`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1"))
    subject.index(tag("gcp:servergroup:two:prod:us-east-1", cloudProvider = "gcp"))

    val results = subject.getAll(null, null, null, null, "aws:", null, null, null, null, 100)
    expectThat(results.map { it.id }).contains("aws:servergroup:one:prod:us-east-1")
  }

  @Test
  fun `getAll filters by namespace via index join`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1",
      tags = listOf("alert" to "warnings", "info" to "default")))
    subject.index(tag("aws:servergroup:two:prod:us-east-1",
      tags = listOf("stack" to "default")))

    val results = subject.getAll(null, null, null, null, null, null, null, "warnings", null, 100)
    expectThat(results).hasSize(1)
    expectThat(results.first().id).isEqualTo("aws:servergroup:one:prod:us-east-1")
  }

  @Test
  fun `getAll filters by tag name`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1",
      tags = listOf("alert" to "default", "stack" to "default")))
    subject.index(tag("aws:servergroup:two:prod:us-east-1",
      tags = listOf("stack" to "default")))

    val results = subject.getAll(null, null, null, null, null, null, null, null, mapOf("alert" to "v"), 100)
    expectThat(results).hasSize(1)
    expectThat(results.first().id).isEqualTo("aws:servergroup:one:prod:us-east-1")
  }

  @Test
  fun `upsert replaces tag_index rows for same id`() {
    val id = "aws:servergroup:one:prod:us-east-1"
    subject.index(tag(id, tags = listOf("a" to "default", "b" to "default", "c" to "default")))
    expectThat(database.context.fetchCount(table("entity_tag_index"))).isEqualTo(3)

    subject.index(tag(id, tags = listOf("a" to "default")))
    expectThat(database.context.fetchCount(table("entity_tag_index"))).isEqualTo(1)
  }

  @Test
  fun `delete removes tag row and index rows`() {
    val id = "aws:servergroup:one:prod:us-east-1"
    subject.index(tag(id, tags = listOf("a" to "default", "b" to "default")))

    subject.delete(id)

    expectThat(database.context.fetchCount(table("entity_tags"))).isEqualTo(0)
    expectThat(database.context.fetchCount(table("entity_tag_index"))).isEqualTo(0)
  }

  @Test
  fun `bulkDelete removes multiple`() {
    val a = tag("aws:servergroup:a:prod:us-east-1", entityId = "a")
    val b = tag("aws:servergroup:b:prod:us-east-1", entityId = "b")
    subject.bulkIndex(listOf(a, b))

    subject.bulkDelete(listOf(a, b))
    expectThat(database.context.fetchCount(table("entity_tags"))).isEqualTo(0)
  }

  @Test
  fun `deleteByNamespace dryRun reports without deleting`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1", tags = listOf("a" to "warnings")))
    subject.index(tag("aws:servergroup:two:prod:us-east-1", tags = listOf("b" to "default")))

    val dry = subject.deleteByNamespace("warnings", dryRun = true, deleteFromSource = false)
    expectThat(dry["matched"]).isEqualTo(1)
    expectThat(database.context.fetchCount(table("entity_tags"))).isEqualTo(2)

    val wet = subject.deleteByNamespace("warnings", dryRun = false, deleteFromSource = false)
    expectThat(wet["deleted"]).isEqualTo(1)
    expectThat(database.context.fetchCount(table("entity_tags"))).isEqualTo(1)
  }

  @Test
  fun `get by id with tag name filter returns empty when tag missing`() {
    val id = "aws:servergroup:one:prod:us-east-1"
    subject.index(tag(id, tags = listOf("stack" to "default")))

    val hit = subject.get(id, mapOf("stack" to "v"))
    expectThat(hit.isPresent).isTrue()

    val miss = subject.get(id, mapOf("nope" to "v"))
    expectThat(miss.isPresent).isEqualTo(false)
  }

  @Test
  fun `get by id returns empty for unknown id`() {
    val miss = subject.get("doesnotexist")
    expectThat(miss.isPresent).isEqualTo(false)
  }

  @Test
  fun `getAll with no filters returns everything up to maxResults`() {
    (1..5).forEach {
      subject.index(tag("aws:servergroup:x$it:prod:us-east-1", entityId = "x$it"))
    }
    val all = subject.getAll(null, null, null, null, null, null, null, null, null, 100)
    expectThat(all).hasSize(5)

    val capped = subject.getAll(null, null, null, null, null, null, null, null, null, 3)
    expectThat(capped).hasSize(3)
  }

  @Test
  fun `delta reports sql count`() {
    subject.index(tag("aws:servergroup:one:prod:us-east-1"))
    subject.index(tag("aws:servergroup:two:prod:us-east-1", entityId = "two"))
    val d = subject.delta()
    expectThat(d["sql"]).isEqualTo(2)
  }

  @Test
  fun `getAll empty on empty table`() {
    val results = subject.getAll(null, "no-such-app", null, null, null, null, null, null, null, 100)
    expectThat(results).isEmpty()
  }

  @Test
  fun `entityRef survives round trip`() {
    val et = tag("aws:servergroup:one:prod:us-east-1")
    subject.index(et)
    val fetched = subject.get(et.id).get()
    expectThat(fetched.entityRef).isNotNull()
    expectThat(fetched.entityRef.cloudProvider).isEqualTo("aws")
    expectThat(fetched.entityRef.application).isEqualTo("spinnaker")
  }
}
