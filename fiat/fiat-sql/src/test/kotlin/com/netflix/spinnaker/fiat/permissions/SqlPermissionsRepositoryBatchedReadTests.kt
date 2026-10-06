/*
 * Copyright 2026 Spinnaker Authors
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
package com.netflix.spinnaker.fiat.permissions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.BuildService
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import java.time.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.contracts.ExperimentalContracts
import kotlinx.coroutines.asCoroutineDispatcher
import org.jooq.DSLContext
import org.jooq.ExecuteContext
import org.jooq.SQLDialect
import org.jooq.impl.DefaultExecuteListener
import org.jooq.impl.DefaultExecuteListenerProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * putAllById reads the existing permission rows for a batch of users in one query rather than one
 * query per user. These tests pin down that the batching does not change what ends up stored, and
 * that the number of reads really is bounded by the batch size.
 */
@ExperimentalContracts
internal class SqlPermissionsRepositoryBatchedReadTests {

  private val userCount = 7
  private val batchSize = 3 // 7 users -> batches of 3, 3 and 1

  /** Counts SELECTs from the permission table (normalized: no quotes, schema or extra whitespace). */
  private class PermissionSelects : DefaultExecuteListener() {
    private val sql = ConcurrentLinkedQueue<String>()

    override fun executeStart(ctx: ExecuteContext) {
      ctx.sql()?.let { sql.add(it.lowercase().replace(Regex("[`\"]"), "").replace(Regex("\\s+"), " ")) }
    }

    fun count() = sql.count { it.startsWith("select") && it.contains("from fiat_permission") }

    fun clear() = sql.clear()
  }

  private class Repo(val repository: SqlPermissionsRepository, val selects: PermissionSelects)

  private companion object {
    // Own database names so these containers stay apart from SqlPermissionsRepositoryTests in one JVM.
    const val MYSQL_URL = "jdbc:tc:mysql:8.0.40:///batchdb"
    const val POSTGRES_URL = "jdbc:tc:postgresql:12-alpine:///batchdb"
    val databases = mutableMapOf<SQLDialect, DSLContext>()
  }

  private var current: DSLContext? = null

  @AfterEach
  fun cleanup() {
    current?.flushAll()
    current = null
  }

  private fun repository(
    jdbcUrl: String,
    dialect: SQLDialect,
    existingBatchSize: Int = batchSize,
    asyncThreads: Int = 0
  ): Repo {
    val jooq = databases.getOrPut(dialect) { initDatabase(jdbcUrl, dialect) }
    current = jooq

    val selects = PermissionSelects()
    val config =
      object : DynamicConfigService.NoopDynamicConfig() {
        private val overrides =
          mutableMapOf<String, Any>(
            "permissions-repository.sql.existing-permissions-batch-size" to existingBatchSize,
            // useAsync() needs more than 2x this many users before it goes async.
            "permissions-repository.sql.read-batch-size" to 1,
            "permissions-repository.sql.max-query-concurrency" to maxOf(asyncThreads, 1)
          )

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getConfig(configType: Class<T>, configName: String, defaultValue: T): T =
          overrides[configName] as T? ?: defaultValue
      }

    return Repo(
      SqlPermissionsRepository(
        Clock.systemUTC(),
        ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL),
        jooq.configuration().derive(DefaultExecuteListenerProvider(selects)).dsl(),
        SqlRetryProperties(),
        listOf(Application(), Account(), BuildService(), ServiceAccount(), Role()),
        if (asyncThreads > 0) Executors.newFixedThreadPool(asyncThreads).asCoroutineDispatcher() else null,
        config
      ),
      selects
    )
  }

  /** Every user shares account0..2 and role0, and has one application and one role of their own. */
  private fun initial(): Map<String, UserPermission> =
    (0 until userCount).associate { u ->
      "user$u" to
        UserPermission()
          .setId("user$u")
          .setAccounts(
            (0..2).map { Account().setName("account$it") }.toSet() +
              // only user0 starts with "late"; see updated()
              (if (u == 0) setOf(Account().setName("late")) else emptySet())
          )
          .setApplications(setOf(Application().setName("app-of-user$u")))
          .setRoles(setOf(Role("role0"), Role("role-of-user$u")))
    }

  /**
   * Drops account1 for everyone, adds account9 for odd users, swaps user3's application and drops
   * user5's own role, and gives user1 and user4 an account that user0 already has. Each change lands in a different batch, so a user's diff must be computed
   * against that user's own rows and nobody else's.
   */
  private fun updated(): Map<String, UserPermission> =
    initial().mapValues { (id, permission) ->
      val u = id.removePrefix("user").toInt()
      permission
        .setAccounts(
          permission.accounts.filter { it.name != "account1" }.toSet() +
            (if (u % 2 == 1) setOf(Account().setName("account9")) else emptySet()) +
            // user1 (same batch as user0) and user4 (another batch) gain a row user0 already holds
            (if (u == 1 || u == 4) setOf(Account().setName("late")) else emptySet())
        )
        .setApplications(if (u == 3) setOf(Application().setName("moved-app")) else permission.applications)
        .setRoles(if (u == 5) setOf(Role("role0")) else permission.roles)
    }

  private fun assertStored(repository: SqlPermissionsRepository, expected: Map<String, UserPermission>) {
    expected.forEach { (id, permission) ->
      val actual = repository.get(id).orElseThrow { AssertionError("no permission stored for $id") }
      assertEquals(permission.accounts.map { it.name }.toSet(), actual.accounts.map { it.name }.toSet(), "$id accounts")
      assertEquals(
        permission.applications.map { it.name }.toSet(),
        actual.applications.map { it.name }.toSet(),
        "$id applications"
      )
      assertEquals(permission.roles.map { it.name }.toSet(), actual.roles.map { it.name }.toSet(), "$id roles")
    }
  }

  private fun insertThenUpdate(repo: Repo) {
    repo.repository.putAllById(initial())
    assertStored(repo.repository, initial())

    repo.repository.putAllById(updated())
    assertStored(repo.repository, updated())

    // Re-applying the same state is a no-op for what's stored.
    repo.repository.putAllById(updated())
    assertStored(repo.repository, updated())
  }

  private fun readsAreBatched(repo: Repo) {
    repo.selects.clear()
    repo.repository.putAllById(initial())

    assertEquals(
      Math.ceil(userCount.toDouble() / batchSize).toInt(),
      repo.selects.count(),
      "one permission read per batch of $batchSize users, not one per user"
    )
  }

  private fun singleBatchWhenSmall(jdbcUrl: String, dialect: SQLDialect) {
    // default batch size (100) comfortably covers all users
    val repo = repository(jdbcUrl, dialect, existingBatchSize = 100)
    repo.repository.putAllById(initial())
    assertEquals(1, repo.selects.count(), "all users fit in a single permission read")
    assertStored(repo.repository, initial())
  }

  private fun unrelatedUsersAreUntouched(repo: Repo) {
    repo.repository.putAllById(initial())
    // update only two users that fall in different batches; everyone else must be left as stored
    val touched = updated().filterKeys { it == "user0" || it == "user5" }
    repo.repository.putAllById(touched)

    val expected = initial().toMutableMap().apply { putAll(touched) }
    assertStored(repo.repository, expected)
  }

  private fun emptyInputIsNoop(repo: Repo) {
    repo.repository.putAllById(emptyMap())
    repo.repository.putAllById(null)
    assertEquals(0, repo.selects.count(), "no reads for empty input")
    assertTrue(repo.repository.isEmpty)
  }

  @Test
  fun `postgres - insert then update across batches`() =
    insertThenUpdate(repository(POSTGRES_URL, SQLDialect.POSTGRES))

  @Test
  fun `postgres - reads are batched`() = readsAreBatched(repository(POSTGRES_URL, SQLDialect.POSTGRES))

  @Test
  fun `postgres - single read when all users fit one batch`() = singleBatchWhenSmall(POSTGRES_URL, SQLDialect.POSTGRES)

  @Test
  fun `postgres - users outside the update are untouched`() =
    unrelatedUsersAreUntouched(repository(POSTGRES_URL, SQLDialect.POSTGRES))

  @Test
  fun `postgres - empty input`() = emptyInputIsNoop(repository(POSTGRES_URL, SQLDialect.POSTGRES))

  @Test
  fun `postgres - async path produces the same result`() =
    insertThenUpdate(repository(POSTGRES_URL, SQLDialect.POSTGRES, asyncThreads = 3))

  @Test
  fun `mysql - insert then update across batches`() = insertThenUpdate(repository(MYSQL_URL, SQLDialect.MYSQL))

  @Test
  fun `mysql - reads are batched`() = readsAreBatched(repository(MYSQL_URL, SQLDialect.MYSQL))

  @Test
  fun `mysql - async path produces the same result`() =
    insertThenUpdate(repository(MYSQL_URL, SQLDialect.MYSQL, asyncThreads = 3))
}
