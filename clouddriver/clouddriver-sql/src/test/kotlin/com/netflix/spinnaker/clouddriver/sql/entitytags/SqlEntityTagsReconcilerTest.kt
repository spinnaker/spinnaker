package com.netflix.spinnaker.clouddriver.sql.entitytags

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlEntityTagsReconcilerTest {

  private val database = SqlTestUtil.initTcMysqlDatabase()!!
  private val clock = Clock.systemDefaultZone()
  private val serverGroups = object : ServerGroupProvider {
    override fun getCloudProviderId() = "aws"
    override fun getServerGroupIdentifiers(account: String?, region: String?) =
      listOf("aws:servergroups:LIVE-v001:prod:us-east-1")
    override fun buildServerGroupIdentifier(account: String?, region: String?, serverGroupName: String?) =
      "aws:servergroups:$serverGroupName:$account:$region"
  }
  private val reconciler = SqlEntityTagsReconciler(listOf(serverGroups), clock)
  private val subject = SqlEntityTagsProvider(database.context, ObjectMapper(), clock, "default", reconciler)

  @AfterEach
  fun cleanup() {
    database.context.deleteFrom(table("entity_tag_index")).execute()
    database.context.deleteFrom(table("entity_tags")).execute()
  }

  private fun tag(id: String, entityId: String, cloudProvider: String = "aws", lastModified: Long = 1000L) =
    EntityTags().also { et ->
      et.id = id
      et.lastModified = lastModified
      et.lastModifiedBy = "tester"
      et.entityRef = EntityTags.EntityRef().also {
        it.cloudProvider = cloudProvider
        it.application = "app"
        it.account = "prod"
        it.region = "us-east-1"
        it.entityType = "servergroup"
        it.entityId = entityId
      }
      et.tags = listOf(
        EntityTags.EntityTag().also {
          it.name = "k"
          it.namespace = "default"
          it.value = "v"
          it.valueType = EntityTags.EntityTagValueType.literal
        }
      )
    }

  private fun ids() = subject.getAll(null, null, null, null, null, null, null, null, null, 100).map { it.id }

  @Test
  fun `removes only old tags whose server group is gone`() {
    subject.bulkIndex(
      listOf(
        tag("live", "live-v001"),
        tag("gone", "gone-v001"),
        tag("recent", "recent-v001", lastModified = clock.millis()),
        tag("other-provider", "x-v001", cloudProvider = "gcp")
      )
    )

    val result = subject.reconcile("aws", "prod", "us-east-1", false)

    expectThat(result["orphanCount"]).isEqualTo(1)
    expectThat(ids()).containsExactlyInAnyOrder("live", "recent", "other-provider")
  }

  @Test
  fun `dry run reports orphans without deleting`() {
    subject.bulkIndex(listOf(tag("live", "live-v001"), tag("gone", "gone-v001")))

    val result = subject.reconcile("aws", "prod", "us-east-1", true)

    expectThat(result["orphanCount"]).isEqualTo(1)
    expectThat(ids()).containsExactlyInAnyOrder("live", "gone")
  }
}
