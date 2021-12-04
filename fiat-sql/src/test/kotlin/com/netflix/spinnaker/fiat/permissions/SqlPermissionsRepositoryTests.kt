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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig.UNRESTRICTED_USERNAME
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.*
import com.netflix.spinnaker.fiat.permissions.SqlPermissionsRepository
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.PERMISSION
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.RESOURCE
import com.netflix.spinnaker.fiat.permissions.sql.tables.references.USER
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import kotlinx.coroutines.newSingleThreadContext
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL.*
import strikt.api.expectThat
import strikt.api.expectCatching
import strikt.assertions.*
import java.lang.UnsupportedOperationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.*
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
internal object SqlPermissionsRepositoryTests : JUnit5Minutests {

    class JooqConfig(val dialect: SQLDialect, val jdbcUrl: String)

    class TestClock(
        private var instant: Instant = Instant.now()
    ) : Clock() {

        override fun getZone(): ZoneId {
            return ZoneId.systemDefault()
        }

        override fun withZone(zone: ZoneId?): Clock {
            throw UnsupportedOperationException()
        }

        override fun instant(): Instant {
            return instant
        }

        fun tick(amount: Duration) {
            this.instant = instant.plus(amount)
        }
    }

    fun ContextBuilder<JooqConfig>.crudOperations(jooqConfig: JooqConfig) {

        val jooq = initDatabase(
            jooqConfig.jdbcUrl,
            jooqConfig.dialect
        )

        val clock = TestClock()

        val objectMapper = ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)

        val extensionResourceType = ResourceType("extension_resource")

        val extensionResource = object : Resource {
            override fun getName() = null
            override fun getResourceType() = extensionResourceType
        }

        val sqlPermissionsRepository = SqlPermissionsRepository(
            clock,
            objectMapper,
            jooq,
            SqlRetryProperties(),
            listOf(Application(), Account(), BuildService(), ServiceAccount(), Role(), extensionResource),
            null,
            DynamicConfigService.NOOP
        )

        context("For ${jooqConfig.dialect}") {

            test("create, update and delete a user") {
                val account1 = Account().setName("account")
                val app1 = Application().setName("app")
                val serviceAccount1 = ServiceAccount()
                    .setName("serviceAccount")
                    .setMemberOf(listOf("role1"))
                val buildService1 = BuildService().setName("build")
                val role1 = Role("role1")
                val userPermission1 = UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(account1))
                    .setApplications(setOf(app1))
                    .setBuildServices(setOf(buildService1))
                    .setServiceAccounts(setOf(serviceAccount1))
                    .setRoles(setOf(role1))

                // verify the user is not found initially
                var userPermission = sqlPermissionsRepository.get(userPermission1.id)
                expectThat(userPermission.isEmpty).isTrue()

                // verify that a user can be created
                sqlPermissionsRepository.put(userPermission1)

                // verify that a user can be retrieved
                val actual = sqlPermissionsRepository.get(userPermission1.id)
                expectThat(actual.isPresent).isTrue()
                expectThat(actual.get().id).isEqualTo(userPermission1.id)
                expectThat(actual.get().isAdmin).isFalse()
                expectThat(actual.get().accounts).containsExactly(account1)
                expectThat(actual.get().applications).containsExactly(app1)
                expectThat(actual.get().buildServices).containsExactly(buildService1)
                expectThat(actual.get().serviceAccounts).containsExactly(serviceAccount1)
                expectThat(actual.get().roles).containsExactly(role1)

                // verify that a user can be deleted
                sqlPermissionsRepository.remove(userPermission1.id)

                userPermission = sqlPermissionsRepository.get(userPermission1.id)
                expectThat(userPermission.isEmpty).isTrue()
            }

            test("should set updated at for user on save") {
                val user1 = UserPermission().setId("testUser")

                sqlPermissionsRepository.put(user1)

                val first = jooq.select(USER.UPDATED_AT)
                    .from(USER)
                    .where(USER.ID.eq(user1.id))
                    .fetchOne(USER.UPDATED_AT)

                user1.isAdmin = true

                clock.tick(Duration.ofSeconds(1))

                sqlPermissionsRepository.put(user1)

                val second = jooq.select(USER.UPDATED_AT)
                    .from(USER)
                    .where(USER.ID.eq(user1.id))
                    .fetchOne(USER.UPDATED_AT)

                expectThat(second).isGreaterThan(first)
            }

            test("should set updated at for resource if changed") {
                val account1 = Account().setName("account1")
                val user1 = UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(account1))

                // insert
                sqlPermissionsRepository.put(user1)

                val first = jooq.select(RESOURCE.UPDATED_AT)
                    .from(RESOURCE).where(
                        RESOURCE.RESOURCE_TYPE.eq(account1.resourceType).and(
                            RESOURCE.RESOURCE_NAME.eq(account1.name)
                        )
                    ).fetchOne(RESOURCE.UPDATED_AT)

                clock.tick(Duration.ofSeconds(1))

                // Insert again should be no update
                sqlPermissionsRepository.put(user1)

                val same = jooq.select(RESOURCE.UPDATED_AT)
                    .from(RESOURCE).where(
                        RESOURCE.RESOURCE_TYPE.eq(account1.resourceType).and(
                            RESOURCE.RESOURCE_NAME.eq(account1.name)
                        )
                    ).fetchOne(RESOURCE.UPDATED_AT)

                expectThat(same).isEqualTo(first)

                clock.tick(Duration.ofSeconds(1))

                // update
                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                account1.setPermissions(abcRead)

                sqlPermissionsRepository.put(user1)

                val second = jooq.select(RESOURCE.UPDATED_AT)
                    .from(RESOURCE).where(
                        RESOURCE.RESOURCE_TYPE.eq(account1.resourceType).and(
                            RESOURCE.RESOURCE_NAME.eq(account1.name)
                        )
                    ).fetchOne(RESOURCE.UPDATED_AT)

                expectThat(second).isGreaterThan(first)
            }

            test("should put the specified permission in the database") {
                val account1 = Account().setName("account")
                val app1 = Application().setName("app")
                val serviceAccount1 = ServiceAccount().setName("serviceAccount")
                    .setMemberOf(listOf("role1"))
                val role1 = Role("role1")

                sqlPermissionsRepository.put(
                    UserPermission()
                        .setId("testUser")
                        .setAccounts(setOf(account1))
                        .setApplications(setOf(app1))
                        .setServiceAccounts(setOf(serviceAccount1))
                        .setRoles(setOf(role1))
                )

                expectThat(
                    jooq.select(USER.ADMIN)
                        .from(USER)
                        .where(USER.ID.eq("testuser"))
                        .fetchOne(USER.ADMIN)
                ).isFalse()

                expectThat(resourceBody(jooq, "testuser", account1.resourceType, account1.name).get())
                    .isEqualTo("""{"name":"account","permissions":{}}""")
                expectThat(resourceBody(jooq, "testuser", app1.resourceType, app1.name).get())
                    .isEqualTo("""{"name":"app","permissions":{},"details":{}}""")
                expectThat(resourceBody(jooq, "testuser", serviceAccount1.resourceType, serviceAccount1.name).get())
                    .isEqualTo("""{"name":"serviceAccount","memberOf":["role1"]}""")
                expectThat(resourceBody(jooq, "testuser", role1.resourceType, role1.name).get())
                    .isEqualTo("""{"name":"role1"}""")
            }

            test("should remove permissions that have been revoked") {
                val testUser = UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(Account().setName("account")))
                    .setApplications(setOf(Application().setName("app")))
                    .setServiceAccounts(setOf(ServiceAccount().setName("serviceAccount")))
                    .setRoles(setOf(Role().setName("role")))

                sqlPermissionsRepository.put(testUser)

                testUser
                    .setAccounts(setOf())
                    .setApplications(setOf())
                    .setServiceAccounts(setOf())
                    .setRoles(setOf())

                sqlPermissionsRepository.put(testUser)

                expectThat(
                    jooq.selectCount().from(PERMISSION).fetchOne(count())
                ).isEqualTo(0)
            }

            test("should delete and update the right permissions") {
                val testUser = UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(Account().setName("account")))
                    .setApplications(setOf(Application().setName("app")))
                    .setServiceAccounts(setOf(ServiceAccount().setName("serviceAccount")))
                    .setRoles(setOf(Role().setName("role")))

                sqlPermissionsRepository.put(testUser)

                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                val account1 = Account().setName("account").setPermissions(abcRead)

                sqlPermissionsRepository.put(
                    testUser
                        .setId("testUser")
                        .setAccounts(setOf(account1))
                        .setApplications(setOf())
                        .setServiceAccounts(setOf())
                        .setRoles(setOf())
                )

                expectThat(jooq.selectCount().from(PERMISSION).fetchOne(count()))
                    .isEqualTo(1)
                expectThat(resourceBody(jooq, "testuser", account1.resourceType, account1.name).get())
                    .isEqualTo("""{"name":"account","permissions":{"READ":["abc"]}}""")
            }

            test("should get the permission out of the database") {
                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                val expected = UserPermission()
                    .setId("testUser")
                    .setAccounts(mutableSetOf(Account().setName("account").setPermissions(abcRead)))
                    .setApplications(mutableSetOf(Application().setName("app").setPermissions(abcRead)))
                    .setServiceAccounts(mutableSetOf(ServiceAccount().setName("serviceAccount")))

                sqlPermissionsRepository.put(expected)

                var actual = sqlPermissionsRepository.get("testuser").get()

                expectThat(actual).isEqualTo(expected)

                val unrestrictedUser = UserPermission()
                    .setId(UNRESTRICTED_USERNAME)
                    .setAccounts(mutableSetOf(Account().setName("unrestrictedAccount")))

                sqlPermissionsRepository.put(unrestrictedUser)

                val result = sqlPermissionsRepository.get("testuser").get()

                expected.addResource(Account().setName("unrestrictedAccount"))
                expectThat(result).isEqualTo(expected)
            }

            test("should put all users to the database and update existing ones") {
                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                val testUser = UserPermission()
                    .setId("testUser")
                    .setAccounts(mutableSetOf(Account().setName("account").setPermissions(abcRead)))
                    .setApplications(mutableSetOf(Application().setName("app").setPermissions(abcRead)))
                    .setServiceAccounts(mutableSetOf(ServiceAccount().setName("serviceAccount")))

                sqlPermissionsRepository.put(testUser)

                expectThat(
                    jooq.select(USER.ADMIN).from(USER).where(USER.ID.eq("testuser")).fetchOne(USER.ADMIN)
                ).isFalse()

                testUser.setAdmin(true)

                val account1 = Account().setName("account1")
                val account2 = Account().setName("account2")

                val testUser1 = UserPermission().setId("testUser1")
                    .setAccounts(setOf(account1))
                val testUser2 = UserPermission().setId("testUser2")
                    .setAccounts(setOf(account2))
                val testUser3 = UserPermission().setId("testUser3")
                    .setAdmin(true)

                sqlPermissionsRepository.putAllById(
                    mutableMapOf(
                        "testuser" to testUser,
                        "testuser1" to testUser1,
                        "testuser2" to testUser2,
                        "testuser3" to testUser3,
                    )
                )

                expectThat(
                    jooq.selectCount().from(USER).fetchOne(count())
                ).isEqualTo(4)
                expectThat(
                    jooq.select(USER.ADMIN).from(USER).where(USER.ID.eq("testuser")).fetchOne(USER.ADMIN)
                ).isTrue()
                expectThat(
                    jooq.select(USER.ADMIN).from(USER).where(USER.ID.eq("testuser3")).fetchOne(USER.ADMIN)
                ).isTrue()
                expectThat(
                    resourceBody(jooq, "testuser1", account1.resourceType, account1.name).get()
                ).isEqualTo("""{"name":"account1","permissions":{}}""")
                expectThat(
                    resourceBody(jooq, "testuser2", account2.resourceType, account2.name).get()
                ).isEqualTo("""{"name":"account2","permissions":{}}""")
                expectThat(
                    resourceBody(jooq, "testuser3", account1.resourceType, account1.name).isEmpty()
                ).isTrue()
                expectThat(
                    resourceBody(jooq, "testuser3", account2.resourceType, account2.name).isEmpty()
                ).isTrue()
            }

            test("should get all users from the database") {
                val account1 = Account().setName("account1")
                val app1 = Application().setName("app1")
                val serviceAccount1 = ServiceAccount().setName("serviceAccount1")

                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                val account2 = Account().setName("account2").setPermissions(abcRead)
                val app2 = Application().setName("app2").setPermissions(abcRead)
                val serviceAccount2 = ServiceAccount().setName("serviceAccount2")

                val testUser1 = UserPermission().setId("testUser1")
                    .setAccounts(setOf(account1))
                    .setApplications(setOf(app1))
                    .setServiceAccounts(setOf(serviceAccount1))
                val testUser2 = UserPermission().setId("testUser2")
                    .setAccounts(setOf(account2))
                    .setApplications(setOf(app2))
                    .setServiceAccounts(setOf(serviceAccount2))
                val testUser3 = UserPermission().setId("testUser3")
                    .setAdmin(true)

                sqlPermissionsRepository.putAllById(
                    mapOf(
                        "testuser1" to testUser1,
                        "testuser2" to testUser2,
                        "testuser3" to testUser3,
                    )
                )

                clock.tick(Duration.ofSeconds(1))

                val result = sqlPermissionsRepository.getAllById()

                expectThat(result).isEqualTo(
                    mapOf(
                        "testuser1" to testUser1.roles,
                        "testuser2" to testUser2.roles,
                        "testuser3" to testUser3.roles
                    )
                )
            }

            test("should delete the specified user") {
                expectThat(jooq.selectCount().from(USER).fetchOne(count())).isEqualTo(0)
                expectThat(jooq.selectCount().from(RESOURCE).fetchOne(count())).isEqualTo(0)
                expectThat(jooq.selectCount().from(PERMISSION).fetchOne(count())).isEqualTo(0)

                val account1 = Account().setName("account")
                val app1 = Application().setName("app")
                val role1 = Role("role1")

                sqlPermissionsRepository.put(
                    UserPermission()
                        .setId("testUser")
                        .setAccounts(setOf(account1))
                        .setApplications(setOf(app1))
                        .setRoles(setOf(role1))
                        .setAdmin(true)
                )

                expectThat(jooq.selectCount().from(USER).fetchOne(count())).isEqualTo(1)
                expectThat(jooq.selectCount().from(RESOURCE).fetchOne(count())).isEqualTo(3)
                expectThat(jooq.selectCount().from(PERMISSION).fetchOne(count())).isEqualTo(3)

                sqlPermissionsRepository.remove("testuser")

                expectThat(jooq.selectCount().from(USER).fetchOne(count())).isEqualTo(0)
                expectThat(jooq.selectCount().from(RESOURCE).fetchOne(count())).isEqualTo(3)
                expectThat(jooq.selectCount().from(PERMISSION).fetchOne(count())).isEqualTo(0)
            }

            test("should get all by roles") {
                val role1 = Role("role1")
                val role2 = Role("role2")
                val role3 = Role("role3")
                val role4 = Role("role4")
                val role5 = Role("role5")

                val acct1 = Account().setName("acct1")

                val user1 = UserPermission().setId("user1").setRoles(setOf(role1, role2))
                val user2 = UserPermission().setId("user2").setRoles(setOf(role1, role3))
                val user3 = UserPermission().setId("user3") // no roles.
                val user4 = UserPermission().setId("user4").setRoles(setOf(role4))
                val user5 = UserPermission().setId("user5").setRoles(setOf(role5))
                val unrestricted = UserPermission().setId(UNRESTRICTED_USERNAME).setAccounts(setOf(acct1))

                sqlPermissionsRepository.putAllById(
                    mapOf(
                        "user1" to user1,
                        "user2" to user2,
                        "user3" to user3,
                        "user4" to user4,
                        "user5" to user5,
                        UNRESTRICTED_USERNAME to unrestricted
                    )
                )

                // Otherwise value of unrestricted user is served from cache
                clock.tick(Duration.ofSeconds(1))

                var result = sqlPermissionsRepository.getAllByRoles(listOf("role1"))

                expectThat(result).isEqualTo(
                    mapOf(
                        "user1" to user1.roles.plus(unrestricted.roles),
                        "user2" to user2.roles.plus(unrestricted.roles),
                        UNRESTRICTED_USERNAME to unrestricted.roles
                    )
                )

                result = sqlPermissionsRepository.getAllByRoles(listOf("role3", "role4"))

                expectThat(result).isEqualTo(
                    mapOf(
                        "user2" to user2.roles.plus(unrestricted.roles),
                        "user4" to user4.roles.plus(unrestricted.roles),
                        UNRESTRICTED_USERNAME to unrestricted.roles
                    )
                )

                result = sqlPermissionsRepository.getAllByRoles(null)

                expectThat(result).isEqualTo(
                    mapOf(
                        "user1" to user1.roles.plus(unrestricted.roles),
                        "user2" to user2.roles.plus(unrestricted.roles),
                        "user3" to user3.roles.plus(unrestricted.roles),
                        "user4" to user4.roles.plus(unrestricted.roles),
                        "user5" to user5.roles.plus(unrestricted.roles),
                        UNRESTRICTED_USERNAME to unrestricted.roles
                    )
                )

                result = sqlPermissionsRepository.getAllByRoles(listOf())

                expectThat(result).isEqualTo(
                    mapOf(
                        UNRESTRICTED_USERNAME to unrestricted.roles
                    )
                )

                result = sqlPermissionsRepository.getAllByRoles(listOf("role5"))

                expectThat(result).isEqualTo(
                    mapOf(
                        "user5" to user5.roles.plus(unrestricted.roles),
                        UNRESTRICTED_USERNAME to unrestricted.roles
                    )
                )
            }

            test("should handle storing extension resources") {
                val resource1 = object : Resource {
                    override fun getName() = "resource1"
                    override fun getResourceType() = extensionResourceType
                }

                sqlPermissionsRepository.put(
                    UserPermission()
                        .setId("testuser")
                        .setExtensionResources(setOf(resource1))
                )

                expectThat(
                    resourceBody(jooq, "testuser", resource1.resourceType, resource1.name).get()
                ).isEqualTo("""{"name":"resource1"}""")
            }

            test("should not delete the unrestricted user on put all") {
                val account1 = Account().setName("account1")
                val unrestricted = UserPermission().setId(UNRESTRICTED_USERNAME).setAccounts(setOf(account1))

                sqlPermissionsRepository.put(unrestricted)

                val testUser = UserPermission().setId("testUser")

                // Otherwise value of unrestricted user is served from cache
                clock.tick(Duration.ofSeconds(1))

                sqlPermissionsRepository.putAllById(mapOf("testuser" to testUser))

                expectThat(jooq.selectCount().from(USER).fetchOne(count()))
                    .isEqualTo(2)
                expectThat(
                    jooq.select(USER.ADMIN).from(USER).where(USER.ID.eq(testUser.id)).fetchOne(USER.ADMIN)
                ).isFalse()
                expectThat(
                    jooq.select(USER.ADMIN).from(USER).where(USER.ID.eq(UNRESTRICTED_USERNAME)).fetchOne(USER.ADMIN)
                ).isFalse()
                expectThat(jooq.selectCount().from(PERMISSION).fetchOne(count()))
                    .isEqualTo(1)
                expectThat(jooq.selectCount().from(RESOURCE).fetchOne(count()))
                    .isEqualTo(1)
                expectThat(resourceBody(jooq, UNRESTRICTED_USERNAME, account1.resourceType, account1.name).get())
                    .isEqualTo("""{"name":"account1","permissions":{}}""")
            }

            test("handle put of multiple user accounts") {
                val roleA = Role("roleA")
                val roleB = Role("roleB").setSource(Role.Source.EXTERNAL)

                val unrestrictedApp = Application().setName("unrestrictedApp")

                val restrictedApp = Application().setName("restrictedApp")
                    .setPermissions(
                        Permissions.Builder().add(Authorization.READ, roleA.name)
                            .build()
                    )

                val unrestrictedAccount = Account().setName("unrestrictedAcct")

                val restrictedAccount = Account().setName("restrictedAcct")
                    .setPermissions(
                        Permissions.Builder().add(Authorization.READ, roleB.name)
                            .build()
                    )

                val unrestrictedUser = UserPermission().setId(UNRESTRICTED_USERNAME)
                    .setAccounts(mutableSetOf(unrestrictedAccount))
                    .setApplications(mutableSetOf(unrestrictedApp))

                val roleAUser = UserPermission().setId("roleAUser")
                    .setRoles(mutableSetOf(roleA))
                    .setApplications(mutableSetOf(restrictedApp))

                val roleBUser = UserPermission().setId("roleBUser")
                    .setRoles(mutableSetOf(roleB))
                    .setAccounts(mutableSetOf(restrictedAccount))

                val roleAroleBUser = UserPermission().setId("roleAroleBUser")
                    .setRoles(mutableSetOf(roleA, roleB))
                    .setAccounts(mutableSetOf(restrictedAccount))
                    .setApplications(mutableSetOf(restrictedApp))

                sqlPermissionsRepository.put(unrestrictedUser)
                sqlPermissionsRepository.put(roleAUser)
                sqlPermissionsRepository.put(roleBUser)
                sqlPermissionsRepository.put(roleAroleBUser)

                clock.tick(Duration.ofSeconds(1))

                expectThat(sqlPermissionsRepository.get(UNRESTRICTED_USERNAME).get()).isEqualTo(unrestrictedUser)
                expectThat(sqlPermissionsRepository.get("roleauser").get()).isEqualTo(roleAUser.merge(unrestrictedUser))
                expectThat(sqlPermissionsRepository.get("rolebuser").get()).isEqualTo(roleBUser.merge(unrestrictedUser))
                expectThat(sqlPermissionsRepository.get("rolearolebuser").get()).isEqualTo(
                    roleAroleBUser.merge(
                        unrestrictedUser
                    )
                )
            }

            test("should handle concurrent write operations without dead lock") {
                val accounts1 = (1..16).asSequence().map { Account().setName("account$it") }.toSet()
                val accounts2 = (8..24).asSequence().map { Account().setName("account$it") }.toSet()
                val apps = (1..16).asSequence().map { Application().setName("app$it") }.toSet()
                val serviceAccounts =
                    (1..16).asSequence().map { ServiceAccount().setName("service_account$it") }.toSet()
                val buildServices = (1..16).asSequence().map { BuildService().setName("service_account$it") }.toSet()
                val roles = (1..16).asSequence().map { Role().setName("role$it") }.toSet()
                val userPermission1 = UserPermission()
                    .setId("testUser1")
                    .setAccounts(accounts1)
                    .setApplications(apps)
                    .setBuildServices(buildServices)
                    .setServiceAccounts(serviceAccounts)
                    .setRoles(roles)
                val userPermission2 = UserPermission()
                    .setId("testUser2")
                    .setAccounts(accounts2)
                    .setApplications(apps)
                    .setBuildServices(buildServices)
                    .setServiceAccounts(serviceAccounts)
                    .setRoles(roles)

                val executor: ThreadPoolExecutor = Executors.newFixedThreadPool(4) as ThreadPoolExecutor
                val callables: MutableList<Callable<Void>> = (1..16).asSequence().map {
                    java.util.concurrent.Callable<Void> {
                        sqlPermissionsRepository.put(userPermission1)
                        sqlPermissionsRepository.putAllById(
                            mutableMapOf(
                                "testuser1" to userPermission1,
                                "testuser2" to userPermission2,
                            )
                        )
                        null
                    }
                }.toMutableList()

                try {
                    val results = executor.invokeAll(callables)
                    results.forEach {
                        expectCatching { it.get() }.isSuccess()
                    }
                } finally {
                    executor.shutdownNow()
                }
            }
        }

        after {
            jooq.flushAll()
        }

        afterAll {
            jooq.close()
        }
    }

    fun tests() = rootContext<JooqConfig> {

        fixture {
           JooqConfig(SQLDialect.MYSQL, SqlTestUtil.tcJdbcUrl)
        }

        context("mysql CRUD operations") {
            crudOperations(JooqConfig(SQLDialect.MYSQL, SqlTestUtil.tcJdbcUrl))
        }

        context("postgresql CRUD operations") {
            crudOperations(
                JooqConfig(
                    SQLDialect.POSTGRES,
                    "jdbc:tc:postgresql:12-alpine:///databasename"
                )
            )
        }
    }

    private fun resourceBody(
        jooq: DSLContext,
        id: String,
        resourceType: ResourceType,
        resourceName: String
    ): Optional<String> {
        return jooq.select(RESOURCE.BODY)
            .from(RESOURCE)
            .join(PERMISSION)
            .on(
                PERMISSION.RESOURCE_TYPE.eq(RESOURCE.RESOURCE_TYPE).and(
                    PERMISSION.RESOURCE_NAME.eq(RESOURCE.RESOURCE_NAME)
                )
            )
            .where(
                PERMISSION.USER_ID.eq(id).and(
                    PERMISSION.RESOURCE_TYPE.eq(resourceType).and(
                        PERMISSION.RESOURCE_NAME.eq(resourceName)
                    )
                )
            )
            .fetchOptional(RESOURCE.BODY)
    }
}
