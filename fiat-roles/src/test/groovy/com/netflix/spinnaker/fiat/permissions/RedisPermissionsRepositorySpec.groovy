/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class RedisPermissionsRepositorySpec extends Specification {

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME

  static String prefix = "unittests"

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)

  @Shared
  Jedis jedis

  @Subject
  RedisPermissionsRepository repo

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    jedis = embeddedRedis.jedis
    jedis.flushDB()
  }

  def setup() {
    repo = new RedisPermissionsRepository(
        objectMapper,
        new JedisClientDelegate(embeddedRedis.pool as JedisPool),
        prefix
    )
  }

  def cleanup() {
    jedis.flushDB()
  }

  def "should put the specified permission in redis"() {
    setup:
    Account account1 = new Account().setName("account")
    Application app1 = new Application().setName("app")
    ServiceAccount serviceAccount1 = new ServiceAccount().setName("serviceAccount")
                                                         .setMemberOf(["role1"])
    Role role1 = new Role("role1")

    when:
    repo.put(new UserPermission()
                 .setId("testUser")
                 .setAccounts([account1] as Set)
                 .setApplications([app1] as Set)
                 .setServiceAccounts([serviceAccount1] as Set)
                 .setRoles([role1] as Set))

    then:
    jedis.smembers("unittests:users") == ["testUser"] as Set
    jedis.smembers("unittests:roles:role1") == ["testUser"] as Set

    jedis.hgetAll("unittests:permissions:testUser:accounts") ==
        ['account': '{"name":"account","permissions":{}}']
    jedis.hgetAll("unittests:permissions:testUser:applications") ==
        ['app': '{"name":"app","permissions":{}}']
    jedis.hgetAll("unittests:permissions:testUser:service_accounts") ==
        ['serviceAccount': '{"name":"serviceAccount","memberOf":["role1"]}']
    jedis.hgetAll("unittests:permissions:testUser:roles") ==
        ['role1': '{"name":"role1"}']
    !jedis.sismember ("unittests:permissions:admin","testUser")
  }

  def "should remove permission that has been revoked"() {
    setup:
    jedis.sadd("unittests:users", "testUser");
    jedis.sadd("unittests:roles:role1", "testUser")
    jedis.hset("unittests:permissions:testUser:accounts",
               "account",
               '{"name":"account","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser:applications",
               "app",
               '{"name":"app","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser:service_accounts",
               "serviceAccount",
               '{"name":"serviceAccount"}')
    jedis.hset("unittests:permissions:testUser:roles",
               "role1",
               '{"name":"role1"}')

    when:
    repo.put(new UserPermission()
                 .setId("testUser")
                 .setAccounts([] as Set)
                 .setApplications([] as Set)
                 .setServiceAccounts([] as Set)
                 .setRoles([] as Set))

    then:
    jedis.hgetAll("unittests:permissions:testUser:accounts") == [:]
    jedis.hgetAll("unittests:permissions:testUser:applications") == [:]
    jedis.hgetAll("unittests:permissions:testUser:service_accounts") == [:]
    jedis.hgetAll("unittests:permissions:testUser:roles") == [:]
    jedis.smembers("unittests:roles:role1") == [] as Set
  }

  def "should get the permission out of redis"() {
    setup:
    jedis.sadd("unittests:users", "testUser");
    jedis.hset("unittests:permissions:testUser:accounts",
               "account",
               '{"name":"account","requiredGroupMembership":["abc"]}')
    jedis.hset("unittests:permissions:testUser:applications",
               "app",
               '{"name":"app","requiredGroupMembership":["abc"]}')
    jedis.hset("unittests:permissions:testUser:service_accounts",
               "serviceAccount",
               '{"name":"serviceAccount"}')

    when:
    def result = repo.get("testUser").get()

    then:
    def expected = new UserPermission()
        .setId("testUser")
        .setAccounts([new Account().setName("account")
                                   .setRequiredGroupMembership(["abc"])] as Set)
        .setApplications([new Application().setName("app")
                                           .setRequiredGroupMembership(["abc"])] as Set)
        .setServiceAccounts([new ServiceAccount().setName("serviceAccount")] as Set)
    result == expected

    when:
    jedis.hset("unittests:permissions:__unrestricted_user__:accounts",
               "account",
               '{"name":"unrestrictedAccount","requiredGroupMembership":[]}')
    result = repo.get("testUser").get()

    then:
    expected.addResource(new Account().setName("unrestrictedAccount"))
    result == expected
  }

  def "should get all users from redis"() {
    setup:
    jedis.sadd("unittests:users", "testUser1", "testUser2", "testUser3");

    and:
    Account account1 = new Account().setName("account1")
    Application app1 = new Application().setName("app1")
    ServiceAccount serviceAccount1 = new ServiceAccount().setName("serviceAccount1")
    jedis.hset("unittests:permissions:testUser1:accounts",
               "account1",
               '{"name":"account1","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser1:applications",
               "app1",
               '{"name":"app1","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser1:service_accounts",
               "serviceAccount1",
               '{"name":"serviceAccount1"}')

    and:
    Account account2 = new Account().setName("account2").setRequiredGroupMembership(["abc"])
    Application app2 = new Application().setName("app2").setRequiredGroupMembership(["abc"])
    ServiceAccount serviceAccount2 = new ServiceAccount().setName("serviceAccount2")
    jedis.hset("unittests:permissions:testUser2:accounts",
               "account2",
               '{"name":"account2","requiredGroupMembership":["abc"]}')
    jedis.hset("unittests:permissions:testUser2:applications",
               "app2",
               '{"name":"app2","requiredGroupMembership":["abc"]}')
    jedis.hset("unittests:permissions:testUser2:service_accounts",
               "serviceAccount2",
               '{"name":"serviceAccount2"}')
    and:
    jedis.sadd("unittests:permissions:admin", "testUser3")

    when:
    def result = repo.getAllById();

    then:
    def testUser1 = new UserPermission().setId("testUser1")
                                        .setAccounts([account1] as Set)
                                        .setApplications([app1] as Set)
                                        .setServiceAccounts([serviceAccount1] as Set)
    def testUser2 = new UserPermission().setId("testUser2")
                                        .setAccounts([account2] as Set)
                                        .setApplications([app2] as Set)
                                        .setServiceAccounts([serviceAccount2] as Set)
    def testUser3 = new UserPermission().setId("testUser3")
                                        .setAdmin(true)
    result == ["testUser1": testUser1, "testUser2": testUser2, "testUser3": testUser3]
  }

  def "should delete the specified user"() {
    given:
    jedis.keys("*").size() == 0

    Account account1 = new Account().setName("account")
    Application app1 = new Application().setName("app")
    Role role1 = new Role("role1")

    when:
    repo.put(new UserPermission()
                 .setId("testUser")
                 .setAccounts([account1] as Set)
                 .setApplications([app1] as Set)
                 .setRoles([role1] as Set)
                 .setAdmin(true))

    then:
    jedis.keys("*").size() == 6 // users, accounts, applications, roles, and reverse-index roles.
    jedis.sismember("unittests:permissions:admin", "testUser")

    when:
    repo.remove("testUser")

    then:
    jedis.keys("*").size() == 0
  }

  def "should get all by roles"() {
    setup:
    def role1 = new Role("role1")
    def role2 = new Role("role2")
    def role3 = new Role("role3")
    def role4 = new Role("role4")

    def acct1 = new Account().setName("acct1")

    def user1 = new UserPermission().setId("user1").setRoles([role1, role2] as Set)
    def user2 = new UserPermission().setId("user2").setRoles([role1, role3] as Set)
    def user3 = new UserPermission().setId("user3") // no roles.
    def user4 = new UserPermission().setId("user4").setRoles([role4] as Set)
    def unrestricted = new UserPermission().setId(UNRESTRICTED).setAccounts([acct1] as Set);

    jedis.hset("unittests:permissions:user1:roles", "role1", '{"name":"role1"}')
    jedis.hset("unittests:permissions:user1:roles", "role2", '{"name":"role2"}')
    jedis.hset("unittests:permissions:user2:roles", "role1", '{"name":"role1"}')
    jedis.hset("unittests:permissions:user2:roles", "role3", '{"name":"role3"}')
    jedis.hset("unittests:permissions:user4:roles", "role4", '{"name":"role4"}')

    jedis.hset("unittests:permissions:__unrestricted_user__:accounts", "acct1", '{"name":"acct1"}')

    jedis.sadd("unittests:roles:role1", "user1", "user2")
    jedis.sadd("unittests:roles:role2", "user1")
    jedis.sadd("unittests:roles:role3", "user2")
    jedis.sadd("unittests:roles:role4", "user4")

    jedis.sadd("unittests:users", "user1", "user2", "user3", "user4", "__unrestricted_user__")

    when:
    def result = repo.getAllByRoles(["role1"])

    then:
    result == ["user1"       : user1.merge(unrestricted),
               "user2"       : user2.merge(unrestricted),
               (UNRESTRICTED): unrestricted]

    when:
    result = repo.getAllByRoles(["role3", "role4"])

    then:
    result == ["user2"       : user2.merge(unrestricted),
               "user4"       : user4.merge(unrestricted),
               (UNRESTRICTED): unrestricted];

    when:
    result = repo.getAllByRoles(null);

    then:
    result == ["user1"       : user1.merge(unrestricted),
               "user2"       : user2.merge(unrestricted),
               "user3"       : user3.merge(unrestricted),
               "user4"       : user4.merge(unrestricted),
               (UNRESTRICTED): unrestricted]

    when:
    result = repo.getAllByRoles([])

    then:
    result == [(UNRESTRICTED): unrestricted]
  }
}
