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

import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig.UNRESTRICTED_USERNAME
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Resource
import com.netflix.spinnaker.fiat.model.resources.ResourceType
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.permissions.sql.Table
import com.netflix.spinnaker.fiat.permissions.sql.Permission
import com.netflix.spinnaker.fiat.permissions.sql.User
import com.netflix.spinnaker.fiat.permissions.sql.transactional
import com.netflix.spinnaker.fiat.permissions.sql.withRetry
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference


class SqlPermissionsRepository(
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
    private val jooq: DSLContext,
    private val sqlRetryProperties: SqlRetryProperties,
    private val poolName: String,
    private val resources: List<Resource>
    ) : PermissionsRepository {

    private val unrestrictedPermission = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(10))
        .build(this::reloadUnrestricted)

    companion object {
        private val log = LoggerFactory.getLogger(SqlPermissionsRepository::class.java)

        private const val NO_UPDATED_AT = 0L

        private val fallbackLastModified = AtomicReference<Long>(null)
    }

    override fun put(permission: UserPermission): PermissionsRepository {
        withPool(poolName) {
            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                val userId = permission.id

                // Create or update user
                ctx
                    .insertInto(
                        Table.USER,
                        User.ID,
                        User.ADMIN,
                        User.UPDATED_AT
                    )
                    .values(userId, permission.isAdmin, clock.millis())
                    .onConflict(User.ID)
                    .doUpdate()
                    .set(mapOf(
                        User.ADMIN to permission.isAdmin,
                        User.UPDATED_AT to clock.millis(),
                    ))
                    .execute()

                // Clear existing permissions
                ctx
                    .deleteFrom(Table.PERMISSION)
                    .where(Permission.USER_ID.eq(userId))
                    .execute()

                // Update permissions
                val insertInto =
                    ctx.insertInto(
                        Table.PERMISSION,
                        Permission.USER_ID,
                        Permission.RESOURCE_NAME,
                        Permission.RESOURCE_TYPE,
                        Permission.BODY
                    )

                permission.allResources.map { r ->
                    val body = objectMapper.writeValueAsString(r)
                    insertInto.values(userId, r.name, r.resourceType.toString(), body)
                }

                insertInto.execute()
            }
        }

        return this
    }

    override fun putAllById(permissions: Map<String, UserPermission>?) {
        permissions?.values?.forEach { permission -> put(permission) }
    }

    override fun get(id: String): Optional<UserPermission> {
        if (UNRESTRICTED_USERNAME == id) {
            return Optional.of(getUnrestrictedUserPermission())
        }
        return getFromDatabase(id)
    }

    override fun getAllById(): Map<String, UserPermission> {
        val resourceTypes = resources.associateBy { r -> r.resourceType.toString() }.toMap()

        val unrestrictedUser = getUnrestrictedUserPermission()

        return withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                ctx.select(User.ID, User.ADMIN, Permission.RESOURCE_TYPE, Permission.BODY)
                    .from(Table.USER)
                    .leftJoin(Table.PERMISSION)
                    .on(User.ID.eq(Permission.USER_ID))
                    .fetch()
                    .groupingBy { r -> r.get(User.ID) }
                    .fold (
                        { k, e -> UserPermission().setId(k).setAdmin(e.get(User.ADMIN)).merge(unrestrictedUser) },
                        { _, acc, e ->
                            val resourceType = resourceTypes[e.get(Permission.RESOURCE_TYPE)]
                            if (resourceType != null) {
                                val resource = objectMapper.readValue(e.get(Permission.BODY), resourceType.javaClass)
                                acc.addResource(resource)
                            }
                            acc
                        }
                    )
            }
        }
    }

    override fun getAllByRoles(anyRoles: List<String>?): Map<String, UserPermission> {
        if (anyRoles == null) {
            return getAllById()
        }

        val result = mutableMapOf<String, UserPermission>()

        val unrestricted = getFromDatabase(UNRESTRICTED_USERNAME)
        if (unrestricted.isPresent) {
            result[UNRESTRICTED_USERNAME] = unrestricted.get()
        }

        if (anyRoles.isEmpty()) {
            return result
        }

        val resourceTypes = resources.associateBy { r -> r.resourceType.toString() }.toMap()

        return withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                ctx.select(User.ID, User.ADMIN, Permission.RESOURCE_TYPE, Permission.BODY)
                    .from(Table.USER)
                    .join(Table.PERMISSION)
                    .on(User.ID.eq(Permission.USER_ID))
                    .where(User.ID.`in`(
                        ctx.select(Permission.USER_ID).from(Table.PERMISSION)
                            .where(Permission.RESOURCE_TYPE.eq(ResourceType.ROLE.toString()).and(Permission.RESOURCE_NAME.`in`(anyRoles)))
                    ))
                    .fetch()
                    .groupingBy { r -> r.get(User.ID) }
                    .foldTo (
                        result,
                        { k, e -> UserPermission().setId(k).setAdmin(e.get(User.ADMIN)).merge(unrestricted.get()) },
                        { _, acc, e ->
                            val resourceType = resourceTypes[e.get(Permission.RESOURCE_TYPE)]
                            if (resourceType != null) {
                                val resource = objectMapper.readValue(e.get(Permission.BODY), resourceType.javaClass)
                                acc.addResource(resource)
                            }
                            acc
                        }
                    )
            }
        }
    }

    override fun remove(id: String) {
        withPool(poolName) {
            jooq.transactional(sqlRetryProperties.transactions) { ctx ->
                // Delete permissions
                jooq.delete(Table.PERMISSION)
                    .where(Permission.USER_ID.eq(id))
                    .execute()

                // Delete user
                ctx.delete(Table.USER)
                    .where(User.ID.eq(id))
                    .execute()
            }
        }
    }

    private fun getFromDatabase(id: String): Optional<UserPermission> {
        val userPermission = UserPermission()
            .setId(id)

        if (id != UNRESTRICTED_USERNAME) {
            val result = withPool(poolName) {
                jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                    ctx.select(User.ADMIN)
                        .from(Table.USER)
                        .where(User.ID.eq(id))
                        .fetchOne()
                }
            }

            if (result == null) {
                log.debug("request for user {} not found in database", id)
                return Optional.empty()
            }

            userPermission.isAdmin = result.get(User.ADMIN)
        }

        withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                resources.forEach { r ->
                    val userResources = ctx
                        .select(
                            Permission.RESOURCE_TYPE,
                            Permission.RESOURCE_NAME,
                            Permission.BODY
                        )
                        .from(Table.PERMISSION)
                        .where(Permission.USER_ID.eq(id).and(Permission.RESOURCE_TYPE.eq(r.resourceType.toString())))
                        .fetch()
                        .map { record ->
                            objectMapper.readValue(record.get(Permission.BODY), r.javaClass)
                        }
                    userPermission.addResources(userResources)
                }
            }
        }

        if (UNRESTRICTED_USERNAME != id) {
            userPermission.merge(getUnrestrictedUserPermission())
        }

        return Optional.of(userPermission)
    }

    private fun getUnrestrictedUserPermission(): UserPermission {
        var serverLastModified = withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
                ctx.select(User.UPDATED_AT)
                    .from(Table.USER)
                    .where(User.ID.eq(UNRESTRICTED_USERNAME))
                    .fetchOne(User.UPDATED_AT)
            }
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
        return getFromDatabase(UNRESTRICTED_USERNAME)
            .map { p ->
                log.debug("reloaded user {} for key {} as {}", UNRESTRICTED_USERNAME, cacheKey, p)
                p
            }
            .orElseThrow {
                log.error(
                    "loading user {} for key {} failed, no permissions returned",
                    UNRESTRICTED_USERNAME,
                    cacheKey
                )
                PermissionRepositoryException("Failed to read unrestricted user")
            }
    }
}

