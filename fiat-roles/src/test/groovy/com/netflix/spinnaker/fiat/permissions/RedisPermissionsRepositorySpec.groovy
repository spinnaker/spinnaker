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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.model.ServiceAccount
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.redis.JedisSource
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.Jedis
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class RedisPermissionsRepositorySpec extends Specification {

  static String prefix = "unittests"

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

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
    JedisSource js = new JedisSource() {
      @Override
      Jedis getJedis() {
        return embeddedRedis.jedis
      }
    }
    repo = new RedisPermissionsRepository()
        .setPrefix(prefix)
        .setObjectMapper(objectMapper)
        .setJedisSource(js)
  }

  def cleanup() {
    jedis.flushDB()
  }

  def "should put the specified permission in redis"() {
    setup:
    Account account1 = new Account().setName("account")
    Application app1 = new Application().setName("app")
    ServiceAccount serviceAccount1 = new ServiceAccount().setName("serviceAccount")

    when:
    repo.put(new UserPermission()
                 .setId("testUser")
                 .setAccounts([account1] as Set)
                 .setApplications([app1] as Set)
                 .setServiceAccounts([serviceAccount1] as Set))

    then:
    jedis.smembers("unittests:users") == ["testUser"] as Set
    jedis.hgetAll("unittests:permissions:testUser:accounts") ==
        ['account': '{"name":"account","requiredGroupMembership":[]}']
    jedis.hgetAll("unittests:permissions:testUser:applications") ==
        ['app': '{"name":"app","requiredGroupMembership":[]}']
    jedis.hgetAll("unittests:permissions:testUser:service_accounts") ==
        ['serviceAccount': '{"name":"serviceAccount"}']
  }

  def "should remove permission that has been revoked"() {
    setup:
    jedis.sadd("unittest:users", "testUser");
    jedis.hset("unittests:permissions:testUser:accounts",
               "account",
               '{"name":"account","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser:applications",
               "app",
               '{"name":"app","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser:service_accounts",
               "serviceAccount",
               '{"name":"serviceAccount"}')

    when:
    repo.put(new UserPermission()
                 .setId("testUser")
                 .setAccounts([] as Set)
                 .setApplications([] as Set)
                 .setServiceAccounts([] as Set))

    then:
    jedis.hgetAll("unittests:permissions:testUser:accounts") == [:]
    jedis.hgetAll("unittests:permissions:testUser:applications") == [:]
    jedis.hgetAll("unittests:permissions:testUser:service_accounts") == [:]
  }

  def "should get the permission out of redis"() {
    setup:
    jedis.sadd("unittest:users", "testUser");
    jedis.hset("unittests:permissions:testUser:accounts",
               "account",
               '{"name":"account","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser:applications",
               "app",
               '{"name":"app","requiredGroupMembership":[]}')
    jedis.hset("unittests:permissions:testUser:service_accounts",
               "serviceAccount",
               '{"name":"serviceAccount"}')

    when:
    def result = repo.get("testUser").get()

    then:
    result
    result.id == "testUser"
    result.accounts == [new Account().setName("account")] as Set
    result.applications == [new Application().setName("app")] as Set
  }

  def "should get all users from redis"() {
    setup:
    jedis.sadd("unittests:users", "testUser1", "testUser2");

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

    when:
    def result = repo.getAllById();

    then:
    result
    result.size() == 2
    result["testUser1"] == new UserPermission().setId("testUser1")
                                               .setAccounts([account1] as Set)
                                               .setApplications([app1] as Set)
                                               .setServiceAccounts([serviceAccount1] as Set)
    result["testUser2"] == new UserPermission().setId("testUser2")
                                               .setAccounts([account2] as Set)
                                               .setApplications([app2] as Set)
                                               .setServiceAccounts([serviceAccount2] as Set)
  }

  def "should delete the specified user"() {
    given:
    jedis.keys("*").size() == 0

    Account account1 = new Account().setName("account")
    Application app1 = new Application().setName("app")

    when:
    repo.put(new UserPermission()
                 .setId("testUser")
                 .setAccounts([account1] as Set)
                 .setApplications([app1] as Set))

    then:
    jedis.keys("*").size() == 3 // users, accounts, and applications.

    when:
    repo.remove("testUser")

    then:
    jedis.keys("*").size() == 0
  }
}
