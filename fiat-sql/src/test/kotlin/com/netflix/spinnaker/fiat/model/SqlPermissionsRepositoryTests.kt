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

package com.netflix.spinnaker.fiat.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig.UNRESTRICTED_USERNAME
import com.netflix.spinnaker.fiat.model.resources.*
import com.netflix.spinnaker.fiat.permissions.SqlPermissionsRepository
import com.netflix.spinnaker.fiat.permissions.sql.Table
import com.netflix.spinnaker.fiat.permissions.sql.User
import com.netflix.spinnaker.fiat.permissions.sql.Permission
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL.*
import strikt.api.expectThat
import strikt.assertions.*
import java.lang.UnsupportedOperationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal object SqlPermissionsRepositoryTests : JUnit5Minutests {

    class JooqConfig(val dialect: SQLDialect, val jdbcUrl: String)

    class TestClock(
        private var instant: Instant = Instant.now()
    ): Clock() {

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

        val sqlPermissionsRepository = SqlPermissionsRepository(
            clock,
            objectMapper,
            jooq,
            SqlRetryProperties(),
            "default",
            listOf(Application(), Account(), BuildService(), ServiceAccount(), Role())
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

                val first = jooq.select(User.UPDATED_AT)
                    .from(Table.USER)
                    .where(User.ID.eq(user1.id))
                    .fetchOne(User.UPDATED_AT)

                user1.isAdmin = true

                clock.tick(Duration.ofSeconds(1))

                sqlPermissionsRepository.put(user1)

                val second = jooq.select(User.UPDATED_AT)
                    .from(Table.USER)
                    .where(User.ID.eq(user1.id))
                    .fetchOne(User.UPDATED_AT)

                expectThat(second).isGreaterThan(first)
            }

            test("should put the specified permission in the database") {
                val account1 = Account().setName("account")
                val app1 = Application().setName("app")
                val serviceAccount1 = ServiceAccount().setName("serviceAccount")
                    .setMemberOf(listOf("role1"))
                val role1 = Role("role1")

                sqlPermissionsRepository.put(UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(account1))
                    .setApplications(setOf(app1))
                    .setServiceAccounts(setOf(serviceAccount1))
                    .setRoles(setOf(role1)))

                expectThat(
                    jooq.select(User.ADMIN)
                        .from(Table.USER)
                        .where(User.ID.eq("testuser"))
                        .fetchOne(User.ADMIN)
                ).isFalse()

                expectThat(
                    jooq.select(Permission.BODY)
                        .from(Table.PERMISSION)
                        .where(
                            Permission.USER_ID.eq("testuser").and(
                                Permission.RESOURCE_TYPE.eq(ResourceType.ACCOUNT.toString())
                            )
                        )
                        .fetchOne(field("body", String::class.java))
                ).isEqualTo("""{"name":"account","permissions":{}}""")
                expectThat(
                    jooq.select(Permission.BODY)
                        .from(Table.PERMISSION)
                        .where(
                            Permission.USER_ID.eq("testuser").and(
                                Permission.RESOURCE_TYPE.eq(ResourceType.APPLICATION.toString())
                            )
                        )
                        .fetchOne(field("body", String::class.java))
                ).isEqualTo("""{"name":"app","permissions":{},"details":{}}""")
                expectThat(
                    jooq.select(Permission.BODY)
                        .from(Table.PERMISSION)
                        .where(
                            Permission.USER_ID.eq("testuser").and(
                                Permission.RESOURCE_TYPE.eq(ResourceType.SERVICE_ACCOUNT.toString())
                            )
                        )
                        .fetchOne(field("body", String::class.java))
                ).isEqualTo("""{"name":"serviceAccount","memberOf":["role1"]}""")
                expectThat(
                    jooq.select(Permission.BODY)
                        .from(Table.PERMISSION)
                        .where(
                            Permission.USER_ID.eq("testuser").and(
                                Permission.RESOURCE_TYPE.eq(ResourceType.ROLE.toString())
                            )
                        )
                        .fetchOne(field("body", String::class.java))
                ).isEqualTo("""{"name":"role1"}""")
            }

            test("should remove permissions that have been revoked") {
                jooq.insertInto(Table.USER, User.ID, User.ADMIN, User.UPDATED_AT)
                    .values("testuser", false, clock.millis())
                    .execute()
                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values("testuser", ResourceType.ACCOUNT.toString(), "account", """{"name":"account","permissions":{}}""")
                    .values("testuser", ResourceType.APPLICATION.toString(), "app", """{"name":"app","permissions":{}}""")
                    .values("testuser", ResourceType.SERVICE_ACCOUNT.toString(), "serviceAccount", """{"name":"serviceAccount","permissions":{}}""")
                    .values("testuser", ResourceType.ROLE.toString(), "role1", """{"name":"role1"}""")
                    .execute()

                sqlPermissionsRepository.put(UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf())
                    .setApplications(setOf())
                    .setServiceAccounts(setOf())
                    .setRoles(setOf()))

                expectThat(
                    jooq.select(count()).from(Table.PERMISSION).fetchOne(count())
                ).isEqualTo(0)
            }

            test("should delete and update the right permissions") {
                jooq.insertInto(Table.USER, User.ID, User.ADMIN, User.UPDATED_AT)
                    .values("testuser", false, clock.millis())
                    .execute()
                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values("testuser", ResourceType.ACCOUNT.toString(), "account", """{"name":"account","permissions":{}}""")
                    .values("testuser", ResourceType.APPLICATION.toString(), "app", """{"name":"app","permissions":{}}""")
                    .values("testuser", ResourceType.SERVICE_ACCOUNT.toString(), "serviceAccount", """{"name":"serviceAccount","permissions":{}}""")
                    .values("testuser", ResourceType.ROLE.toString(), "role1", """{"name":"role1"}""")
                    .execute()

                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()

                val account1 = Account().setName("account").setPermissions(abcRead)

                sqlPermissionsRepository.put(UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(account1))
                    .setApplications(setOf())
                    .setServiceAccounts(setOf())
                    .setRoles(setOf()))

                expectThat(
                    jooq.select(count()).from(Table.PERMISSION).fetchOne(count())
                ).isEqualTo(1)
                expectThat(
                    jooq.select(Permission.BODY)
                        .from(Table.PERMISSION)
                        .where(
                            Permission.USER_ID.eq("testuser").and(
                                Permission.RESOURCE_TYPE.eq(ResourceType.ACCOUNT.toString())
                            )
                        )
                        .fetchOne(field("body", String::class.java))
                ).isEqualTo("""{"name":"account","permissions":{"READ":["abc"]}}""")
            }


            test("should get the permission out of the database") {
                jooq.insertInto(Table.USER, User.ID, User.ADMIN, User.UPDATED_AT)
                    .values("testuser", false, clock.millis())
                    .execute()
                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values("testuser", ResourceType.ACCOUNT.toString(), "account","""{"name":"account","permissions":{"READ":["abc"]}}""")
                    .values("testuser", ResourceType.APPLICATION.toString(), "app", """{"name":"app","permissions":{"READ":["abc"]}}""")
                    .values("testuser", ResourceType.SERVICE_ACCOUNT.toString(), "serviceAccount", """{"name":"serviceAccount"}""")
                    .execute()

                var result = sqlPermissionsRepository.get("testuser").get()

                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                val expected = UserPermission()
                    .setId("testUser")
                    .setAccounts(mutableSetOf(Account().setName("account").setPermissions(abcRead)))
                    .setApplications(mutableSetOf(Application().setName("app").setPermissions(abcRead)))
                    .setServiceAccounts(mutableSetOf(ServiceAccount().setName("serviceAccount")))

                expectThat(result).isEqualTo(expected)

                jooq.insertInto(Table.USER, User.ID, User.ADMIN, User.UPDATED_AT)
                    .values(UNRESTRICTED_USERNAME, false, clock.millis())
                    .execute()
                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values(UNRESTRICTED_USERNAME, "account", "unrestrictedAccount","""{"name":"unrestrictedAccount","permissions":{}}""")
                    .execute()

                result = sqlPermissionsRepository.get("testuser").get()

                expected.addResource(Account().setName("unrestrictedAccount"))
                expectThat(result).isEqualTo(expected)
            }

            test("should get all users from the database") {
                jooq.insertInto(Table.USER, User.ID, User.ADMIN, User.UPDATED_AT)
                    .values("testuser1", false, clock.millis())
                    .values("testuser2", false, clock.millis())
                    .values("testuser3", true, clock.millis())
                    .execute()

                // User 1
                val account1 = Account().setName("account1")
                val app1 = Application().setName("app1")
                val serviceAccount1 = ServiceAccount().setName("serviceAccount1")
                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values("testuser1", "${account1.resourceType}", account1.name,"""{"name":"account1","permissions":{}}""")
                    .values("testuser1", "${app1.resourceType}", app1.name, """{"name":"app1","permissions":{}}""")
                    .values("testuser1", "${serviceAccount1.resourceType}", serviceAccount1.name, """{"name":"serviceAccount1"}""")
                    .execute()

                // User 2
                val abcRead = Permissions.Builder().add(Authorization.READ, "abc").build()
                val account2 = Account().setName("account2").setPermissions(abcRead)
                val app2 = Application().setName("app2").setPermissions(abcRead)
                val serviceAccount2 = ServiceAccount().setName("serviceAccount2")
                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values("testuser2", "${account2.resourceType}", account2.name, """{"name":"account2","permissions":{"READ":["abc"]}}""")
                    .values("testuser2", "${app2.resourceType}", app2.name, """{"name":"app2","permissions":{"READ":["abc"]}}""")
                    .values("testuser2", "${serviceAccount2.resourceType}", serviceAccount2.name, """{"name":"serviceAccount2"}""")
                    .execute()

                val result = sqlPermissionsRepository.getAllById()

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

                expectThat(result).isEqualTo(mapOf(
                    "testuser1" to testUser1,
                    "testuser2" to testUser2,
                    "testuser3" to testUser3
                ))
            }

            test ("should delete the specified user") {
                expectThat(jooq.select(count()).from(Table.USER).fetchOne(count())).isEqualTo(0)
                expectThat(jooq.select(count()).from(Table.PERMISSION).fetchOne(count())).isEqualTo(0)

                val account1 = Account().setName("account")
                val app1 = Application().setName("app")
                val role1 = Role("role1")

                sqlPermissionsRepository.put(UserPermission()
                    .setId("testUser")
                    .setAccounts(setOf(account1))
                    .setApplications(setOf(app1))
                    .setRoles(setOf(role1))
                    .setAdmin(true)
                )

                expectThat(jooq.select(count()).from(Table.USER).fetchOne(count())).isEqualTo(1)                // accounts, applications, roles
                expectThat(jooq.select(count()).from(Table.PERMISSION).fetchOne(count())).isEqualTo(3)

                sqlPermissionsRepository.remove("testuser")

                expectThat(jooq.select(count()).from(Table.USER).fetchOne(count())).isEqualTo(0)
                expectThat(jooq.select(count()).from(Table.PERMISSION).fetchOne(count())).isEqualTo(0)
            }

            test ("should get all by roles") {
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

                jooq.insertInto(Table.PERMISSION, Permission.USER_ID, Permission.RESOURCE_TYPE, Permission.RESOURCE_NAME, Permission.BODY)
                    .values("user1", "${role1.resourceType}", role1.name, """{"name":"role1"}""")
                    .values("user1", "${role2.resourceType}", role2.name, """{"name":"role2"}""")
                    .values("user2", "${role1.resourceType}", role1.name, """{"name":"role1"}""")
                    .values("user2", "${role3.resourceType}", role3.name, """{"name":"role3"}""")
                    .values("user4", "${role4.resourceType}", role4.name, """{"name":"role4"}""")
                    .values("user5", "${role5.resourceType}", role5.name, """{"name":"role5"}""")
                    .values(UNRESTRICTED_USERNAME, "${acct1.resourceType}", acct1.name, """{"name":"acct1"}""")
                    .execute()

                // Otherwise value of unrestricted user is served from cache
                clock.tick(Duration.ofSeconds(1))

                jooq.insertInto(Table.USER, User.ID, User.UPDATED_AT)
                    .values("user1", clock.millis())
                    .values("user2", clock.millis())
                    .values("user3", clock.millis())
                    .values("user4", clock.millis())
                    .values("user5", clock.millis())
                    .values(UNRESTRICTED_USERNAME, clock.millis())
                    .execute()

                var result = sqlPermissionsRepository.getAllByRoles(listOf("role1"))

                expectThat(result).isEqualTo(mapOf(
                    "user1" to user1.merge(unrestricted),
                    "user2" to user2.merge(unrestricted),
                    UNRESTRICTED_USERNAME to unrestricted
                ))

                result = sqlPermissionsRepository.getAllByRoles(listOf("role3", "role4"))

                expectThat(result).isEqualTo(mapOf(
                    "user2" to user2.merge(unrestricted),
                    "user4" to user4.merge(unrestricted),
                    UNRESTRICTED_USERNAME to unrestricted
                ))

                result = sqlPermissionsRepository.getAllByRoles(null)

                expectThat(result).isEqualTo(mapOf(
                    "user1" to user1.merge(unrestricted),
                    "user2" to user2.merge(unrestricted),
                    "user3" to user3.merge(unrestricted),
                    "user4" to user4.merge(unrestricted),
                    "user5" to user5.merge(unrestricted),
                    UNRESTRICTED_USERNAME to unrestricted
                ))

                result = sqlPermissionsRepository.getAllByRoles(listOf())

                expectThat(result).isEqualTo(mapOf(
                    UNRESTRICTED_USERNAME to unrestricted
                ))

                result = sqlPermissionsRepository.getAllByRoles(listOf("role5"))

                expectThat(result).isEqualTo(mapOf(
                    "user5" to user5.merge(unrestricted),
                    UNRESTRICTED_USERNAME to unrestricted
                ))
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
            JooqConfig(SQLDialect.MYSQL, "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename")
        }

        context("mysql CRUD operations") {
            crudOperations(JooqConfig(SQLDialect.MYSQL, "jdbc:tc:mysql:5.7.22://somehostname:someport/databasename"))
        }

        context("postgresql CRUD operations") {
            crudOperations(JooqConfig(SQLDialect.POSTGRES, "jdbc:tc:postgresql:12-alpine://somehostname:someport/databasename"))
        }
    }
}