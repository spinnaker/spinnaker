/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.cats.sql.cache

import com.google.common.hash.Hashing
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry
import com.netflix.spinnaker.cats.provider.ProviderRegistry
import com.netflix.spinnaker.cats.test.TestAgent
import com.netflix.spinnaker.cats.test.TestProvider
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.ObjectProvider
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class SqlUnknownAgentCleanupAgentTest : JUnit5Minutests {

  companion object {
    private const val idProd = "aws:instances:prod:us-east-1:i-abcd1234"
    private const val idTest = "aws:instances:test:us-east-1:i-abcd1234"
    private const val agentProd = "prod/TestAgent"
    private const val agentTest = "test/TestAgent"
    private const val relIdProd = "aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000"
    private const val relIdTest = "aws:serverGroups:myapp-test:test:us-east-1:myapp-test-v000"
    private const val relAgentProd = "serverGroups:prod/TestAgent"
    private const val relAgentTest = "serverGroups:test/TestAgent"
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      SqlTestUtil.cleanupDb(dslContext)
      dslContext.close()
    }

    context("test and prod accounts exist") {
      deriveFixture {
        fixture.providerAgents.addAll(
          listOf(
            testCachingAgent(),
            prodCachingAgent()
          )
        )
        seedDatabase(includeTestAccount = true, includeProdAccount = true)
        fixture
      }

      test("nothing happens") {
        expect {
          that(selectAllResources()).describedAs("initial resources").hasSize(2)
          that(selectAllRels()).describedAs("initial relationships").hasSize(2)
        }

        subject.run()

        expect {
          that(selectAllResources()).describedAs("modified resources").hasSize(2)
          that(selectAllRels()).describedAs("modified relationships").hasSize(2)
        }
      }

      context("test account is removed") {
        modifyFixture {
          fixture.providerAgents.removeIf { it.scope == "test" }
        }

        before { subject.run() }

        test("relationships referencing old data are deleted") {
          expectThat(selectAllResources())
            .hasSize(1)[0].isEqualTo(idProd)
        }

        test("resources referencing old data are deleted") {
          expectThat(selectAllRels())
            .hasSize(1)[0].isEqualTo(relIdProd)
        }
      }
    }
    context("add caching agent for uninstantiated custom resource definition") {
      deriveFixture {
        fixture.providerAgents.add(unregisteredCustomResourceCachingAgent())
        fixture
      }

      test("error is not thrown when table does not exist for type for which agent is authoritative") {
        assertDoesNotThrow {
          subject.run()
        }
      }
    }
  }

  private inner class Fixture {
    val testDatabase = SqlTestUtil.initTcMysqlDatabase()
    val dslContext = testDatabase.context

    val providerAgents: MutableList<TestAgent> = mutableListOf()
    val providerRegistry: ProviderRegistry = DefaultProviderRegistry(
      listOf(TestProvider(providerAgents as Collection<CachingAgent>)),
      InMemoryNamedCacheFactory()
    )
    val registry = NoopRegistry()

    val subject =
      SqlUnknownAgentCleanupAgent(
        StaticObjectProvider(providerRegistry),
        dslContext,
        registry,
        SqlNames()
      )

    fun seedDatabase(includeTestAccount: Boolean, includeProdAccount: Boolean) {
      SqlNames().run {
        val resource = resourceTableName("instances")
        val rel = relTableName("instances")
        dslContext.execute("CREATE TABLE IF NOT EXISTS $resource LIKE cats_v2_resource_template")
        dslContext.execute("CREATE TABLE IF NOT EXISTS $rel LIKE cats_v2_rel_template")
      }

      dslContext.insertInto(table("cats_v2_instances"))
        .columns(
          field("id_hash"),
          field("id"),
          field("agent_hash"),
          field("agent"),
          field("application"),
          field("body_hash"),
          field("body"),
          field("last_updated")
        )
        .let {
          if (includeProdAccount) {
            it
              .values(
                getSha256Hash(idProd),
                idProd,
                getSha256Hash(agentProd),
                agentProd,
                "myapp",
                "",
                "",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .let {
          if (includeTestAccount) {
            it
              .values(
                getSha256Hash(idTest),
                idTest,
                getSha256Hash(agentTest),
                agentTest,
                "myapp",
                "",
                "",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .execute()

      dslContext.insertInto(table("cats_v2_instances_rel"))
        .columns(
          field("uuid"),
          field("id_hash"),
          field("id"),
          field("rel_id_hash"),
          field("rel_id"),
          field("rel_agent_hash"),
          field("rel_agent"),
          field("rel_type"),
          field("last_updated")
        )
        .let {
          if (includeProdAccount) {
            it
              .values(
                ULID().nextULID(),
                getSha256Hash(idProd),
                idProd,
                getSha256Hash(relIdProd),
                relIdProd,
                getSha256Hash(relAgentProd),
                relAgentProd,
                "serverGroups",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .let {
          if (includeTestAccount) {
            it
              .values(
                ULID().nextULID(),
                getSha256Hash(idTest),
                idTest,
                getSha256Hash(relIdTest),
                relIdTest,
                getSha256Hash(relAgentTest),
                relAgentTest,
                "serverGroups",
                System.currentTimeMillis()
              )
          } else {
            it
          }
        }
        .execute()
    }

    fun testCachingAgent(): TestAgent =
      TestAgent().also {
        it.scope = "test"
        it.types = setOf(INSTANCES.ns, SERVER_GROUPS.ns)
        it.authoritative = setOf(INSTANCES.ns)
        it.results = mapOf(
          INSTANCES.ns to listOf(
            DefaultCacheData(
              idTest,
              mapOf(),
              mapOf(
                SERVER_GROUPS.ns to listOf(relIdTest)
              )
            )
          )
        )
      }

    fun prodCachingAgent(): TestAgent =
      TestAgent().also {
        it.scope = "prod"
        it.types = setOf(INSTANCES.ns, SERVER_GROUPS.ns)
        it.authoritative = setOf(INSTANCES.ns)
        it.results = mapOf(
          INSTANCES.ns to listOf(
            DefaultCacheData(
              idProd,
              mapOf(),
              mapOf(
                SERVER_GROUPS.ns to listOf(relIdProd)
              )
            )
          )
        )
      }

    fun unregisteredCustomResourceCachingAgent(): TestAgent =
      TestAgent().also {
        it.scope = "unregisteredCustomResources"
        it.types = setOf("cloud.google.com.BackendConfig")
        it.authoritative = setOf("cloud.google.com.BackendConfig")
      }

    fun selectAllResources(): List<String> =
      dslContext.select(field("id"))
        .from(table(SqlNames().resourceTableName("instances")))
        .fetch(0, String::class.java)

    fun selectAllRels(): List<String> =
      dslContext.select(field("rel_id"))
        .from(table(SqlNames().relTableName("instances")))
        .fetch(0, String::class.java)

    fun getSha256Hash(str: String): String =
      Hashing.sha256().hashBytes(str.toByteArray()).toString()
  }

  private inner class StaticObjectProvider(val obj: ProviderRegistry) :
    ObjectProvider<ProviderRegistry> {
    override fun getIfUnique(): ProviderRegistry? = obj
    override fun getObject(vararg args: Any?): ProviderRegistry = obj
    override fun getObject(): ProviderRegistry = obj
    override fun getIfAvailable(): ProviderRegistry? = obj
  }
}
