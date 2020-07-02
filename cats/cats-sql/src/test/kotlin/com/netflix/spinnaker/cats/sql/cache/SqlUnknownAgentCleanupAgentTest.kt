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

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      SqlTestUtil.cleanupDb(dslContext)
      dslContext.close()
    }

    context("test and prod accounts exist") {
      deriveFixture {
        fixture.providerAgents.addAll(listOf(
          testCachingAgent(),
          prodCachingAgent()
        ))
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
            .hasSize(1)[0].isEqualTo("aws:instances:prod:us-east-1:i-abcd1234")
        }

        test("resources referencing old data are deleted") {
          expectThat(selectAllRels())
            .hasSize(1)[0].isEqualTo("aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000")
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

    val subject = SqlUnknownAgentCleanupAgent(StaticObjectProvider(providerRegistry), dslContext, registry, SqlNames())

    fun seedDatabase(includeTestAccount: Boolean, includeProdAccount: Boolean) {
      SqlNames().run {
        val resource = resourceTableName("instances")
        val rel = relTableName("instances")
        dslContext.execute("CREATE TABLE IF NOT EXISTS $resource LIKE cats_v1_resource_template")
        dslContext.execute("CREATE TABLE IF NOT EXISTS $rel LIKE cats_v1_rel_template")
      }

      dslContext.insertInto(table("cats_v1_instances"))
        .columns(
          field("id"), field("agent"), field("application"), field("body_hash"), field("body"), field("last_updated")
        )
        .let {
          if (includeProdAccount) {
            it
              .values(
                "aws:instances:prod:us-east-1:i-abcd1234",
                "prod/TestAgent",
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
                "aws:instances:test:us-east-1:i-abcd1234",
                "test/TestAgent",
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

      dslContext.insertInto(table("cats_v1_instances_rel"))
        .columns(
          field("uuid"),
          field("id"),
          field("rel_id"),
          field("rel_agent"),
          field("rel_type"),
          field("last_updated")
        )
        .let {
          if (includeProdAccount) {
            it
              .values(
                ULID().nextULID(),
                "aws:instances:prod:us-east-1:i-abcd1234",
                "aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000",
                "serverGroups:prod/TestAgent",
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
                "aws:instances:test:us-east-1:i-abcd1234",
                "aws:serverGroups:myapp-test:test:us-east-1:myapp-test-v000",
                "serverGroups:test/TestAgent",
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
                          "aws:instances:test:us-east-1:i-abcd1234",
                          mapOf(),
                          mapOf(
                                  SERVER_GROUPS.ns to listOf(
                                          "aws:serverGroups:myapp-test:test:us-east-1:myapp-test-v000"
                                  )
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
                          "aws:instances:prod:us-east-1:i-abcd1234",
                          mapOf(),
                          mapOf(
                                  SERVER_GROUPS.ns to listOf(
                                          "aws:serverGroups:myapp-prod:prod:us-east-1:myapp-prod-v000"
                                  )
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
  }

  private inner class StaticObjectProvider(val obj: ProviderRegistry) : ObjectProvider<ProviderRegistry> {
    override fun getIfUnique(): ProviderRegistry? = obj
    override fun getObject(vararg args: Any?): ProviderRegistry = obj
    override fun getObject(): ProviderRegistry = obj
    override fun getIfAvailable(): ProviderRegistry? = obj
  }
}
