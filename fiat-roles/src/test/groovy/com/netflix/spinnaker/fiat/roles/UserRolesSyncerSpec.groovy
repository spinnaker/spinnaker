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

package com.netflix.spinnaker.fiat.roles

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.config.ResourceProvidersHealthIndicator
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver
import com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository
import com.netflix.spinnaker.fiat.providers.ResourceProvider
import com.netflix.spinnaker.fiat.redis.JedisSource
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.springframework.boot.actuate.health.Health
import redis.clients.jedis.Jedis
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UserRolesSyncerSpec extends Specification {

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME;

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)

  @Shared
  Jedis jedis

  @Shared
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
        .setObjectMapper(objectMapper)
        .setJedisSource(js)
  }

  def cleanup() {
    jedis.flushDB()
  }

  def "should update user roles & add service accounts"() {
    setup:
    def extRole = new Role("extRole").setSource(Role.Source.EXTERNAL)
    def user1 = new UserPermission()
        .setId("user1")
        .setAccounts([new Account().setName("account1")] as Set)
        .setRoles([extRole] as Set)
    def user2 = new UserPermission()
        .setId("user2")
        .setAccounts([new Account().setName("account2")] as Set)
    def unrestrictedUser = new UserPermission()
        .setId(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME)
        .setAccounts([new Account().setName("unrestrictedAccount")] as Set)

    def abcServiceAcct = new UserPermission().setId("abc")
    def xyzServiceAcct = new UserPermission().setId("xyz@domain.com")

    repo.put(user1)
    repo.put(user2)
    repo.put(unrestrictedUser)

    def newUser2 = new UserPermission()
        .setId("user2")
        .setAccounts([new Account().setName("account3")] as Set)

    def serviceAccountProvider = Mock(ResourceProvider) {
      getAll() >> [new ServiceAccount().setName("abc"),
                   new ServiceAccount().setName("xyz@domain.com")]
    }

    def permissionsResolver = Mock(PermissionsResolver)
    @Subject syncer = new UserRolesSyncer()
        .setPermissionsRepository(repo)
        .setPermissionsResolver(permissionsResolver)
        .setServiceAccountProvider(serviceAccountProvider)
        .setHealthIndicator(new AlwaysUpHealthIndicator())

    expect:
    repo.getAllById() == [
        "user1"       : user1.merge(unrestrictedUser),
        "user2"       : user2.merge(unrestrictedUser),
        (UNRESTRICTED): unrestrictedUser
    ]

    when:
    syncer.syncAndReturn()

    then:
    permissionsResolver.resolve(_ as List) >> ["user1"         : user1,
                                               "user2"         : newUser2,
                                               "abc"           : abcServiceAcct,
                                               "xyz@domain.com": xyzServiceAcct]
    permissionsResolver.resolveUnrestrictedUser() >> unrestrictedUser

    expect:
    repo.getAllById() == [
        "user1"         : user1.merge(unrestrictedUser),
        "user2"         : newUser2.merge(unrestrictedUser),
        "abc"           : abcServiceAcct.merge(unrestrictedUser),
        "xyz@domain.com": xyzServiceAcct.merge(unrestrictedUser),
        (UNRESTRICTED)  : unrestrictedUser
    ]
  }

  class AlwaysUpHealthIndicator extends ResourceProvidersHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
      builder.up()
    }
  }
}
