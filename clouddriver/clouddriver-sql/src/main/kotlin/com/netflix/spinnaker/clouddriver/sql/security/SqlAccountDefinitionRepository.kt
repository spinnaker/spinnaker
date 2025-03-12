/*
 * Copyright 2021, 2022 Apple Inc.
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
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionTypes
import com.netflix.spinnaker.clouddriver.sql.read
import com.netflix.spinnaker.clouddriver.sql.transactional
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition
import com.netflix.spinnaker.kork.secrets.SecretException
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.apache.logging.log4j.LogManager
import org.jooq.*
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.*
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
        ctx.select(bodyColumn)
          .from(accountsTable)
          .where(idColumn.eq(name))
          .fetchOne { (json) ->
            mapper.deserialize(json.data())
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
            deserializeAccountData(json.data())
          }
          .filterNotNullTo(mutableListOf())
      }
    }

  override fun listByType(typeName: String): MutableList<out CredentialsDefinition> =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(bodyColumn)
          .from(accountsTable)
          .where(typeColumn.eq(typeName))
          .fetch { (json) ->
            deserializeAccountData(json.data())
          }
          .filterNotNullTo(mutableListOf())
      }
    }

  private fun deserializeAccountData(accountData: String): CredentialsDefinition? =
    try {
      mapper.deserialize(accountData)
    } catch (e: SecretException) {
      LOGGER.warn("Unable to decrypt secret in account data ($accountData). Skipping this account.", e)
      null
    } catch (e: Exception) {
      // invalid data usually isn't stored in the database, hence an error rather than warning
      LOGGER.error("Invalid account data loaded ($accountData). Skipping this account; consider deleting or fixing it.", e)
      null
    }

  private fun getCredentialsType(definition: CredentialsDefinition): String {
    val javaClass = definition.javaClass
    return AccountDefinitionTypes.getCredentialsTypeName(javaClass)
      ?: throw IllegalArgumentException("No @CredentialsType annotation found on $javaClass")
  }

  override fun create(definition: CredentialsDefinition) {
    withPool(poolName) {
      val name = definition.name
      val typeName = getCredentialsType(definition)
      val timestamp = clock.millis()
      val user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
      val body = JSON.valueOf(mapper.serialize(definition))
      try {
        jooq.transactional { ctx ->
          ctx.insertInto(accountsTable)
            .set(idColumn, name)
            .set(typeColumn, typeName)
            .set(bodyColumn, body)
            .set(createdColumn, timestamp)
            .set(lastModifiedColumn, timestamp)
            .set(modifiedByColumn, user)
            .execute()
          ctx.insertInto(accountHistoryTable)
            .set(idColumn, name)
            .set(typeColumn, typeName)
            .set(bodyColumn, body)
            .set(lastModifiedColumn, timestamp)
            .set(versionColumn, findLatestVersion(name))
            .execute()
        }
      } catch (e: DataAccessException) {
        throw SqlAccountDefinitionException("Cannot create account with definition $body", e)
      }
      // TODO(jvz): CredentialsDefinitionNotifier::definitionChanged for https://github.com/spinnaker/kork/pull/958
    }
  }

  override fun save(definition: CredentialsDefinition) {
    withPool(poolName) {
      val name = definition.name
      val typeName = getCredentialsType(definition)
      val timestamp = clock.millis()
      val user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
      val body = JSON.valueOf(mapper.serialize(definition))
      try {
        jooq.transactional { ctx ->
          ctx.insertInto(accountsTable)
            .set(idColumn, name)
            .set(typeColumn, typeName)
            .set(bodyColumn, body)
            .set(createdColumn, timestamp)
            .set(lastModifiedColumn, timestamp)
            .set(modifiedByColumn, user)
            .run {
              if (jooq.dialect() == SQLDialect.POSTGRES) onConflict(idColumn).doUpdate()
              else onDuplicateKeyUpdate()
            }
            .set(bodyColumn, body)
            .set(lastModifiedColumn, timestamp)
            .set(modifiedByColumn, user)
            .execute()
          ctx.insertInto(accountHistoryTable)
            .set(idColumn, name)
            .set(typeColumn, typeName)
            .set(bodyColumn, body)
            .set(lastModifiedColumn, timestamp)
            .set(versionColumn, findLatestVersion(name))
            .execute()
        }
      } catch (e: DataAccessException) {
        throw SqlAccountDefinitionException("Cannot save account with definition $body", e)
      }
      // TODO(jvz): CredentialsDefinitionNotifier::definitionChanged for https://github.com/spinnaker/kork/pull/958
    }
  }

  override fun update(definition: CredentialsDefinition) {
    withPool(poolName) {
      val name = definition.name
      val typeName = getCredentialsType(definition)
      val timestamp = clock.millis()
      val user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
      val body = JSON.valueOf(mapper.serialize(definition))
      try {
        jooq.transactional { ctx ->
          val rows = ctx.update(accountsTable)
            .set(bodyColumn, body)
            .set(lastModifiedColumn, timestamp)
            .set(modifiedByColumn, user)
            .where(idColumn.eq(name))
            .execute()
          if (rows != 1) {
            throw NotFoundException("No account found with name $name")
          }
          ctx.insertInto(accountHistoryTable)
            .set(idColumn, name)
            .set(typeColumn, typeName)
            .set(bodyColumn, body)
            .set(lastModifiedColumn, timestamp)
            .set(versionColumn, findLatestVersion(name))
            .execute()
        }
      } catch (e: DataAccessException) {
        throw SqlAccountDefinitionException("Cannot update account with definition $body", e)
      }
      // TODO(jvz): CredentialsDefinitionNotifier::definitionChanged for https://github.com/spinnaker/kork/pull/958
    }
  }

  override fun delete(name: String) {
    withPool(poolName) {
      val typeName = jooq.read { ctx ->
        ctx.select(typeColumn)
          .from(accountsTable)
          .where(idColumn.eq(name))
          .fetchOne(typeColumn)
      } ?: throw NotFoundException("No account found with name $name")
      try {
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
      } catch (e: DataAccessException) {
        throw SqlAccountDefinitionException("Cannot delete account with name $name", e)
      }
      // TODO(jvz): CredentialsDefinitionNotifier::definitionRemoved for https://github.com/spinnaker/kork/pull/958
    }
  }

  private fun findLatestVersion(name: String): Select<Record1<Int>> =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(count(versionColumn) + 1)
          .from(accountHistoryTable)
          .where(idColumn.eq(name))
      }
    }

  override fun revisionHistory(name: String): MutableList<AccountDefinitionRepository.Revision> =
    withPool(poolName) {
      jooq.read { ctx ->
        ctx.select(bodyColumn, versionColumn, lastModifiedColumn)
          .from(accountHistoryTable)
          .where(idColumn.eq(name))
          .orderBy(versionColumn.desc())
          .fetch { (body, version, timestamp) -> AccountDefinitionRepository.Revision(
            version,
            timestamp,
            body?.let { mapper.deserialize(it.data()) }
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
    private val LOGGER = LogManager.getLogger(SqlAccountDefinitionRepository::class.java)
  }
}
