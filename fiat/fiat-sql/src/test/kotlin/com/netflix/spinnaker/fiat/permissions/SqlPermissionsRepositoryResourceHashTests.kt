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
import kotlin.contracts.ExperimentalContracts
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * A resource is skipped on write only when *its own* stored hash is unchanged. Two different
 * resources can serialize to the same JSON (e.g. a build service and an account with the same name
 * and no other fields), so a hash that merely exists somewhere in the table must not make another
 * resource look up to date.
 */
@ExperimentalContracts
internal class SqlPermissionsRepositoryResourceHashTests {

  private companion object {
    const val MYSQL_URL = "jdbc:tc:mysql:8.0.40:///hashdb"
    const val POSTGRES_URL = "jdbc:tc:postgresql:12-alpine:///hashdb"
    val databases = mutableMapOf<SQLDialect, DSLContext>()
  }

  private val objectMapper = ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
  private var current: DSLContext? = null

  @AfterEach
  fun cleanup() {
    current?.flushAll()
    current = null
  }

  private fun repository(jdbcUrl: String, dialect: SQLDialect): SqlPermissionsRepository {
    val jooq = databases.getOrPut(dialect) { initDatabase(jdbcUrl, dialect) }
    current = jooq
    return SqlPermissionsRepository(
      Clock.systemUTC(),
      objectMapper,
      jooq,
      SqlRetryProperties(),
      listOf(Application(), Account(), BuildService(), ServiceAccount(), Role()),
      null,
      DynamicConfigService.NOOP
    )
  }

  private fun resourceWithIdenticalBodyIsStored(repository: SqlPermissionsRepository) {
    val account = Account().setName("shared")
    val buildService = BuildService().setName("shared")
    // The scenario only exists if the two bodies really are identical.
    assertEquals(
      objectMapper.writeValueAsString(account),
      objectMapper.writeValueAsString(buildService),
      "precondition: account and build service serialize identically"
    )
    assertNotEquals(account.resourceType, buildService.resourceType)

    // The account is stored first, so its hash is already in the table when the build service arrives.
    repository.put(UserPermission().setId("first").setAccounts(setOf(account)))
    repository.put(UserPermission().setId("second").setBuildServices(setOf(buildService)))

    val actual = repository.get("second").orElseThrow { AssertionError("second not stored") }
    assertEquals(setOf("shared"), actual.buildServices.map { it.name }.toSet())
    assertEquals(emptySet<String>(), actual.accounts.map { it.name }.toSet())

    val first = repository.get("first").orElseThrow { AssertionError("first not stored") }
    assertEquals(setOf("shared"), first.accounts.map { it.name }.toSet())
  }

  @Test
  fun `postgres - identical body under another resource type is still stored`() =
    resourceWithIdenticalBodyIsStored(repository(POSTGRES_URL, SQLDialect.POSTGRES))

  @Test
  fun `mysql - identical body under another resource type is still stored`() =
    resourceWithIdenticalBodyIsStored(repository(MYSQL_URL, SQLDialect.MYSQL))
}
