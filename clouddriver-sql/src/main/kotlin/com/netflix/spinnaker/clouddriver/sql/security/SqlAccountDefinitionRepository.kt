/*
 * Copyright 2021 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.sql.security

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionMapper
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository
import com.netflix.spinnaker.clouddriver.sql.read
import com.netflix.spinnaker.clouddriver.sql.transactional
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import java.time.Clock

class SqlAccountDefinitionRepository(
  private val jooq: DSLContext,
  private val mapper: AccountDefinitionMapper,
  private val clock: Clock,
  private val poolName: String
) : AccountDefinitionRepository {

  override fun getByName(name: String): CredentialsDefinition? =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(bodyColumn, typeColumn)
          .from(accountsTable)
          .where(idColumn.eq(name))
          .fetchOne { (json, type) ->
            mapper.convertFromString(json.data(), type)
          }
      }
    }

  override fun listByType(
    typeName: String,
    limit: Int,
    startingAccountName: String?
  ): MutableList<out CredentialsDefinition> =
    withPool(poolName) {
      jooq.read { ctx ->
        val conditions = mutableListOf(typeColumn.eq(typeName))
        startingAccountName?.let { conditions += idColumn.ge(it) }
        ctx.select(bodyColumn)
          .from(accountsTable)
          .where(conditions)
          .orderBy(idColumn)
          .limit(limit)
          .fetch { (json) ->
            mapper.convertFromString(json.data(), typeName)
          }
      }
    }

  override fun listByType(typeName: String): MutableList<out CredentialsDefinition> =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(bodyColumn)
          .from(accountsTable)
          .where(typeColumn.eq(typeName))
          .fetch { (json) ->
            mapper.convertFromString(json.data(), typeName)
          }
      }
    }

  override fun create(definition: CredentialsDefinition) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        val timestamp = clock.millis()
        val user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
        val body = JSON.valueOf(mapper.convertToString(definition))
        val typeName = AccountDefinitionMapper.getJsonTypeName(definition.javaClass)
        ctx.insertInto(accountsTable)
          .set(idColumn, definition.name)
          .set(typeColumn, typeName)
          .set(bodyColumn, body)
          .set(createdColumn, timestamp)
          .set(lastModifiedColumn, timestamp)
          .set(modifiedByColumn, user)
          .execute()
        ctx.insertInto(accountHistoryTable)
          .set(idColumn, definition.name)
          .set(typeColumn, typeName)
          .set(bodyColumn, body)
          .set(lastModifiedColumn, timestamp)
          .set(versionColumn, 1)
          .execute()
      }
    }
  }

  override fun update(definition: CredentialsDefinition) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        val timestamp = clock.millis()
        val user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
        val body = JSON.valueOf(mapper.convertToString(definition))
        ctx.update(accountsTable)
          .set(bodyColumn, body)
          .set(lastModifiedColumn, timestamp)
          .set(modifiedByColumn, user)
          .where(idColumn.eq(definition.name))
          .execute()
        ctx.insertInto(accountHistoryTable)
          .set(idColumn, definition.name)
          .set(typeColumn, AccountDefinitionMapper.getJsonTypeName(definition.javaClass))
          .set(bodyColumn, body)
          .set(lastModifiedColumn, timestamp)
          .set(versionColumn, findLatestVersion(definition.name))
          .execute()
      }
    }
  }

  override fun delete(name: String) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.insertInto(accountHistoryTable)
          .set(idColumn, name)
          .set(deletedColumn, true)
          .set(lastModifiedColumn, clock.millis())
          .set(versionColumn, findLatestVersion(name))
          .execute()
        ctx.deleteFrom(accountsTable)
          .where(idColumn.eq(name))
          .execute()
      }
    }
  }

  private fun findLatestVersion(name: String): Select<Record1<Int>> =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(versionColumn + 1)
          .from(accountHistoryTable)
          .where(idColumn.eq(name))
          .orderBy(versionColumn.desc())
          .limit(1)
      }
    }

  override fun revisionHistory(name: String): MutableList<AccountDefinitionRepository.Revision> =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(bodyColumn, typeColumn, versionColumn, lastModifiedColumn)
          .from(accountHistoryTable)
          .where(idColumn.eq(name))
          .orderBy(versionColumn.desc())
          .fetch { (body, type, version, timestamp) -> AccountDefinitionRepository.Revision(
            version,
            timestamp,
            body?.let { mapper.convertFromString(it.data(), type) }
          ) }
      }
    }

  companion object {
    private val accountsTable = table("accounts")
    private val accountHistoryTable = table("accounts_history")
    private val idColumn = field("id", String::class.java)
    private val bodyColumn = field("body", JSON::class.java)
    private val typeColumn = field("type", String::class.java)
    private val deletedColumn = field("is_deleted", Boolean::class.java)
    private val createdColumn = field("created_at", Long::class.java)
    private val lastModifiedColumn = field("last_modified_at", Long::class.java)
    private val modifiedByColumn = field("last_modified_by", String::class.java)
    private val versionColumn = field("version", Int::class.java)
  }
}
