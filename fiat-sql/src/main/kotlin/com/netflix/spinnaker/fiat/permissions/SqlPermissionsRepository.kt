/*
 * Copyright 2021 Expedia, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.common.hash.Hashing
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig.UNRESTRICTED_USERNAME
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.permissions.sql.SqlUtil
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.PERMISSION
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.RESOURCE
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.USER
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import kotlinx.coroutines.*
import org.jooq.*
import org.jooq.exception.DataAccessException
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL.field
import org.jooq.impl.SQLDataType
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PreDestroy
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

@ExperimentalContracts
class SqlPermissionsRepository(
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    private val jooq: DSLContext,
    private val sqlRetryProperties: SqlRetryProperties,
    resources: List<Resource>,
    private val coroutineContext: CoroutineContext?,
    private val dynamicConfigService: DynamicConfigService
) : PermissionsRepository {

    private val unrestrictedPermission = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .build(this::reloadUnrestricted)

    private val resourceTypes = resources.associateBy { r -> r.resourceType }.toMap()

    companion object {
        private val log = LoggerFactory.getLogger(SqlPermissionsRepository::class.java)

        private const val NO_UPDATED_AT = 0L

        private val fallbackLastModified = AtomicReference<Long>(null)
    }

    override fun put(permission: UserPermission): PermissionsRepository {
        putAllById(mapOf(permission.id to permission))
        return this
    }

    override fun putAllById(permissions: Map<String, UserPermission>?) {
        if (permissions == null || permissions.isEmpty()) {
            return
        }

        // NOTE: This is potentially very slow since `allResources` creates a set from five other sets per-user
        val allResources = permissions.values
            .asSequence()
            .flatMap { it.allResources }
            .toSet()

        putResources(allResources)

        if (coroutineContext.useAsync(permissions.size, this::useAsync)) {
            permissions.values.chunked(
                dynamicConfigService.getConfig(Int::class.java, "permissions-repository.sql.max-query-concurrency", 4)
            ).forEach { chunk ->
                val scope = SqlCoroutineScope(coroutineContext)

                val deferred = chunk.map {
                    scope.async { putUserPermission(it) }
                }

                runBlocking {
                    deferred.awaitAll()
                }
            }
        } else {
            permissions.values.forEach { putUserPermission(it) }
        }
    }

    override fun get(id: String): Optional<UserPermission> {
        val unrestricted = getUnrestrictedUserPermission()
        if (UNRESTRICTED_USERNAME == id) {
            return Optional.of(unrestricted)
        }
        return getUserPermissions(id, unrestricted)
    }

    private fun getUserPermissions(id: String, unrestrictedUser: UserPermission?): Optional<UserPermission> {
        // Check if the user exists and if they do are they an admin
        val isAdmin = withRetry(RetryCategory.READ) {
            jooq.select(USER.ADMIN)
                .from(USER)
                .where(USER.ID.eq(id))
                .fetchOne(USER.ADMIN)
        } ?: return Optional.empty()

        val userPermission = UserPermission()
            .setId(id)
            .setAdmin(isAdmin)

        val resourceIds = getUserPermissionsRecords(id)
        val resourceRecords = fetchResourceRecords(resourceIds)

        resourceIds.mapNotNull { resourceRecords[it] }.forEach {
            userPermission.addResource(it)
        }

        if (unrestrictedUser != null) {
            userPermission.merge(unrestrictedUser)
        }

        return Optional.of(userPermission)
    }

    override fun getAllById(): Map<String, Set<Role>> {
        return this.getUserRoles(emptySet())
    }

    override fun getAllByRoles(anyRoles: List<String>?): Map<String, Set<Role>> {
        if (anyRoles == null) {
            return getAllById()
        } else if (anyRoles.isEmpty()) {
            val unrestricted = getUnrestrictedUserPermission()
            return mapOf(UNRESTRICTED_USERNAME to unrestricted.roles)
        }

        val idsWithRoles = jooq.select(USER.ID)
            .from(USER)
            .leftSemiJoin(PERMISSION)
            .on(
                PERMISSION.USER_ID
                    .eq(USER.ID)
                    .and(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE))
                    .and(PERMISSION.RESOURCE_NAME.`in`(*anyRoles.toTypedArray()))
            )
            .fetch()
            .intoSet(USER.ID)
            .plus(UNRESTRICTED_USERNAME) // ensure unrestricted user is included in results

        return getUserRoles(idsWithRoles)
    }

    override fun remove(id: String) {
        withRetry(RetryCategory.WRITE) {
            // Delete permissions
            jooq.delete(PERMISSION)
                .where(PERMISSION.USER_ID.eq(id))
                .execute()

            // Delete user
            jooq.delete(USER)
                .where(USER.ID.eq(id))
                .execute()
        }
    }

    private fun getUserRoles(ids: Set<String>): Map<String, Set<Role>> {
        val allRoles = withRetry(RetryCategory.READ) {
            jooq.select(RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
                .from(RESOURCE)
                .where(RESOURCE.RESOURCE_TYPE.eq(ResourceType.ROLE))
                .fetchMap(RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
        }
            .mapValues { objectMapper.readValue(it.value, Role::class.java) }

        if (ids.isEmpty()) {
            return withRetry(RetryCategory.READ) {
                jooq.select(USER.ID, PERMISSION.RESOURCE_NAME)
                    .from(USER)
                    .leftJoin(PERMISSION)
                    .on(USER.ID.eq(PERMISSION.USER_ID).and(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE)))
                    .fetchGroups(USER.ID, PERMISSION.RESOURCE_NAME)
            }
                .mapValues { record -> record.value.mapNotNull { allRoles[it] }.toSet() }
        }

        return withRetry(RetryCategory.READ) {
            jooq.select(USER.ID, PERMISSION.RESOURCE_NAME)
                .from(USER)
                .leftJoin(PERMISSION)
                .on(USER.ID.eq(PERMISSION.USER_ID).and(PERMISSION.RESOURCE_TYPE.eq(ResourceType.ROLE)))
                .where(USER.ID.`in`(ids))
                .fetchGroups(USER.ID, PERMISSION.RESOURCE_NAME)
        }
            .mapValues { record -> record.value.mapNotNull { allRoles[it] }.toSet() }
    }

    private fun fetchResourceRecords(ids: Collection<ResourceId>): Map<ResourceId, Resource> {
        val resources = mutableMapOf<ResourceId, Resource>()

        ids.groupBy { it.type }.forEach { (type, resourceIds) ->
            val resourceForType = resourceTypes[type] ?: return@forEach
            val objectReaderForType = objectMapper.readerFor(resourceForType.javaClass)

            resourceIds.chunked(
                dynamicConfigService.getConfig(Int::class.java, "permissions-repository.sql.read-batch-size", 300)
            ) { chunk ->
                val names = chunk.map { it.name }.sorted()

                withRetry(RetryCategory.READ) {
                    jooq.select(RESOURCE.RESOURCE_NAME, RESOURCE.BODY)
                        .from(RESOURCE)
                        .where(RESOURCE.RESOURCE_TYPE.eq(type).and(RESOURCE.RESOURCE_NAME.`in`(*names.toTypedArray())))
                        .fetch()
                        .forEach {
                            resources[ResourceId(type, it.get(RESOURCE.RESOURCE_NAME))] =
                                objectReaderForType.readValue(it.get(RESOURCE.BODY)) as Resource
                        }
                }
            }
        }

        return resources
    }

    private fun putUserPermission(permission: UserPermission) {
        val insert = jooq.insertInto(USER, USER.ID, USER.ADMIN, USER.UPDATED_AT)

        insert.apply {
            values(permission.id, permission.isAdmin, clock.millis())
            // https://github.com/jOOQ/jOOQ/issues/5975 means we have to duplicate field names here
            when (jooq.dialect()) {
                SQLDialect.POSTGRES ->
                    onConflict(USER.ID)
                        .doUpdate()
                        .set(USER.ADMIN, SqlUtil.excluded(field("admin", SQLDataType.BOOLEAN)))
                        .set(USER.UPDATED_AT, SqlUtil.excluded(field("updated_at", SQLDataType.BIGINT)))
                else ->
                    onDuplicateKeyUpdate()
                        .set(USER.ADMIN, MySQLDSL.values(field("admin", SQLDataType.BOOLEAN)))
                        .set(USER.UPDATED_AT, MySQLDSL.values(field("updated_at", SQLDataType.BIGINT)))
            }
        }

        withRetry(RetryCategory.WRITE) {
            insert.execute()
        }

        putUserPermissions(permission.id, permission.allResources)
    }

    private fun putUserPermissions(id: String, resources: Set<Resource>) {
        val writeBatchSize = dynamicConfigService.getConfig(Int::class.java, "permissions-repository.sql.write-batch-size", 100)

        val existingPermissions = getUserPermissionsRecords(id)

        val currentPermissions = mutableSetOf<ResourceId>() // current permissions from request
        val toStore = mutableListOf<ResourceId>() // ids that are new or changed

        resources.forEach {
            val resourceId = ResourceId(it.resourceType, it.name)
            currentPermissions.add(resourceId)

            if (!existingPermissions.contains(resourceId)) {
                toStore.add(resourceId)
            }
        }

        toStore.chunked(writeBatchSize) { chunk ->
            val insert = jooq.insertInto(
                PERMISSION,
                PERMISSION.USER_ID,
                PERMISSION.RESOURCE_TYPE,
                PERMISSION.RESOURCE_NAME
            )

            insert.apply {
                chunk.forEach { resource ->
                    values(id, resource.type, resource.name)
                    when (jooq.dialect()) {
                        SQLDialect.POSTGRES ->
                            onConflictDoNothing()
                        else ->
                            onDuplicateKeyIgnore()
                    }
                }
            }

            withRetry(RetryCategory.WRITE) {
                insert.execute()
            }
        }

        val toDelete = existingPermissions.minus(currentPermissions)

        try {
            toDelete.groupBy { it.type }
                .forEach { (type, ids) ->
                    ids.chunked(writeBatchSize) { chunk ->
                        val names = chunk.map { it.name }.sorted()
                        withRetry(RetryCategory.WRITE) {
                            jooq.deleteFrom(PERMISSION)
                                .where(
                                    PERMISSION.USER_ID.eq(id).and(
                                        PERMISSION.RESOURCE_TYPE.eq(type).and(
                                            PERMISSION.RESOURCE_NAME.`in`(*names.toTypedArray())
                                        )
                                    )
                                )
                                .execute()
                        }
                    }
                }
        } catch (e: Exception) {
            log.error("error deleting old permissions", e)
        }
    }

    private fun getUserPermissionsRecords(id: String) =
        withRetry(RetryCategory.READ) {
            jooq
                .select(PERMISSION.RESOURCE_TYPE, PERMISSION.RESOURCE_NAME)
                .from(PERMISSION)
                .where(PERMISSION.USER_ID.eq(id))
                .fetchSet { ResourceId(it.get(PERMISSION.RESOURCE_TYPE), it.get(PERMISSION.RESOURCE_NAME)) }
        }

    private fun putResources(resources: Set<Resource>, cleanup: Boolean = false) {
        val existingHashIds = getResourceHashes()

        val existingHashes = existingHashIds.values.toSet()
        val existingIds = existingHashIds.keys

        val currentIds = mutableSetOf<ResourceId>() // current resources from the request
        val toStore = mutableListOf<ResourceId>() // ids that are new or changed
        val bodies = mutableMapOf<ResourceId, String>() // id to body
        val hashes = mutableMapOf<ResourceId, String>() // id to sha256(body)

        resources.forEach {
            val id = ResourceId(it.resourceType, it.name)
            currentIds.add(id)

            val body: String? = objectMapper.writeValueAsString(it)
            val bodyHash = getHash(body)

            if (body != null && bodyHash != null && !existingHashes.contains(bodyHash)) {
                toStore.add(id)
                bodies[id] = body
                hashes[id] = bodyHash
            }
        }

        val now = clock.millis()

        toStore.chunked(
            dynamicConfigService.getConfig(
                Int::class.java,
                "permissions-repository.sql.write-batch-size",
                100
            )
        ) { chunk ->
            try {
                val insert = jooq.insertInto(
                    RESOURCE,
                    RESOURCE.RESOURCE_TYPE,
                    RESOURCE.RESOURCE_NAME,
                    RESOURCE.BODY,
                    RESOURCE.BODY_HASH,
                    RESOURCE.UPDATED_AT
                )

                insert.apply {
                    chunk.forEach {
                        values(it.type, it.name, bodies[it], hashes[it], now)
                        when (jooq.dialect()) {
                            // https://github.com/jOOQ/jOOQ/issues/5975 means we have to duplicate field names here
                            SQLDialect.POSTGRES ->
                                onConflict(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME)
                                    .doUpdate()
                                    .set(RESOURCE.BODY, SqlUtil.excluded(field("body", SQLDataType.LONGVARCHAR)))
                                    .set(RESOURCE.BODY_HASH, SqlUtil.excluded(field("body_hash", SQLDataType.VARCHAR)))
                                    .set(RESOURCE.UPDATED_AT, SqlUtil.excluded(field("updated_at", SQLDataType.BIGINT)))
                            else ->
                                onDuplicateKeyUpdate()
                                    .set(RESOURCE.BODY, MySQLDSL.values(field("body", SQLDataType.LONGVARCHAR)))
                                    .set(RESOURCE.BODY_HASH, MySQLDSL.values(field("body_hash", SQLDataType.VARCHAR)))
                                    .set(RESOURCE.UPDATED_AT, MySQLDSL.values(field("updated_at", SQLDataType.BIGINT)))
                        }
                    }
                }

                withRetry(RetryCategory.WRITE) {
                    insert.execute()
                }
            } catch (e: DataAccessException) {
                log.error("Error inserting ids: $chunk", e)
            }
        }

        if (cleanup) {
            val toDelete = existingIds.minus(currentIds)

            deleteResources(toDelete)
        }
    }

    private fun getResourceHashes() =
        withRetry(RetryCategory.READ) {
            jooq
                .select(RESOURCE.RESOURCE_TYPE, RESOURCE.RESOURCE_NAME, RESOURCE.BODY_HASH)
                .from(RESOURCE)
                .fetchMap(
                    { ResourceId(it.get(RESOURCE.RESOURCE_TYPE), it.get(RESOURCE.RESOURCE_NAME)) },
                    { it.get(RESOURCE.BODY_HASH) }
                )
        }

    data class ResourceId(
        val type: ResourceType,
        val name: String
    )

    private fun deleteResources(ids: Collection<ResourceId>) {
        try {
            ids
                .groupBy { it.type }
                .forEach { (type, idsForType) ->
                    val names = idsForType.map { it.name }
                    withRetry(RetryCategory.WRITE) {
                        jooq.deleteFrom(RESOURCE)
                            .where(
                                RESOURCE.RESOURCE_TYPE.eq(type).and(
                                    RESOURCE.RESOURCE_NAME.`in`(*names.toTypedArray())
                                )
                            )
                            .execute()
                    }
                }
        } catch (e: Exception) {
            log.error("Error deleting old resources", e)
        }
    }

    private fun getUnrestrictedUserPermission(): UserPermission {
        var serverLastModified = withRetry(RetryCategory.READ) {
            jooq.select(USER.UPDATED_AT)
                .from(USER)
                .where(USER.ID.eq(UNRESTRICTED_USERNAME))
                .fetchOne(USER.UPDATED_AT)
        }

        if (serverLastModified == null) {
            log.debug(
                "no last modified time available in database for user {} using default of {}",
                UNRESTRICTED_USERNAME,
                NO_UPDATED_AT
            )
            serverLastModified = NO_UPDATED_AT
        }

        return try {
            val userPermission = unrestrictedPermission[serverLastModified]
            if (userPermission != null && serverLastModified != NO_UPDATED_AT) {
                fallbackLastModified.set(serverLastModified)
            }
            userPermission!!
        } catch (ex: Throwable) {
            log.error(
                "failed reading user {} from cache for key {}", UNRESTRICTED_USERNAME, serverLastModified, ex
            )
            val fallback = fallbackLastModified.get()
            if (fallback != null) {
                val fallbackPermission = unrestrictedPermission.getIfPresent(fallback)
                if (fallbackPermission != null) {
                    log.warn(
                        "serving fallback permission for user {} from key {} as {}",
                        UNRESTRICTED_USERNAME,
                        fallback,
                        fallbackPermission
                    )
                    return fallbackPermission
                }
                log.warn("no fallback entry remaining in cache for key {}", fallback)
            }
            if (ex is RuntimeException) {
                throw ex
            }
            throw IntegrationException(ex)
        }
    }

    private fun reloadUnrestricted(cacheKey: Long): UserPermission {
        try {
            val unrestricted = getUserPermissions(UNRESTRICTED_USERNAME, null)
            if (!unrestricted.isEmpty) {
                log.debug("reloaded user {} for key {} as {}", UNRESTRICTED_USERNAME, cacheKey, unrestricted)
                return unrestricted.get()
            }
            return UserPermission().setId(UNRESTRICTED_USERNAME)
        } catch (e: Exception) {
            log.error(
                "loading user {} for key {} failed, no permissions returned",
                UNRESTRICTED_USERNAME,
                cacheKey,
                e
            )
            throw PermissionRepositoryException("Failed to read unrestricted user", e)
        }
    }

    private fun getHash(body: String?): String? {
        if (body.isNullOrBlank()) {
            return null
        }

        return Hashing.sha256().hashString(body, Charsets.UTF_8).toString()
    }

    // Lifted from SqlCache.kt in clouddriver

    // TODO: Does this belong in kork-sql?
    private enum class RetryCategory {
        WRITE, READ
    }

    private fun <T> withRetry(category: RetryCategory, action: () -> T): T {
        return if (category == RetryCategory.WRITE) {
            val retry = Retry.of(
                "sqlWrite",
                RetryConfig.custom<T>()
                    .maxAttempts(sqlRetryProperties.transactions.maxRetries)
                    .waitDuration(Duration.ofMillis(sqlRetryProperties.transactions.backoffMs))
                    .ignoreExceptions(SQLDialectNotSupportedException::class.java)
                    .build()
            )

            Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
        } else {
            val retry = Retry.of(
                "sqlRead",
                RetryConfig.custom<T>()
                    .maxAttempts(sqlRetryProperties.reads.maxRetries)
                    .waitDuration(Duration.ofMillis(sqlRetryProperties.reads.backoffMs))
                    .ignoreExceptions(SQLDialectNotSupportedException::class.java)
                    .build()
            )

            Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
        }
    }

    @ExperimentalContracts
    private fun useAsync(items: Int): Boolean {
        return dynamicConfigService.getConfig(Int::class.java, "permissions-repository.sql.max-query-concurrency", 4) > 1 &&
            items > dynamicConfigService.getConfig(Int::class.java, "permissions-repository.sql.read-batch-size", 500) * 2
    }

    @ExperimentalContracts
    private fun asyncEnabled(): Boolean {
        return dynamicConfigService.getConfig(Int::class.java, "permissions-repository.sql.max-query-concurrency", 4) > 1
    }
}

@ExperimentalContracts
fun CoroutineContext?.useAsync(size: Int, useAsync: (size: Int) -> Boolean): Boolean {
    contract {
        returns(true) implies (this@useAsync is CoroutineContext)
    }

    return this != null && useAsync.invoke(size)
}

@ExperimentalContracts
fun CoroutineContext?.useAsync(useAsync: () -> Boolean): Boolean {
    contract {
        returns(true) implies (this@useAsync is CoroutineContext)
    }

    return this != null && useAsync.invoke()
}

class SqlCoroutineScope(context: CoroutineContext) : CoroutineScope {
    override val coroutineContext = context
    private val jobs = Job()

    @PreDestroy
    fun killChildJobs() = jobs.cancel()
}
