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
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.config.ResourceProvidersHealthIndicator
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.BuildService
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver
import com.netflix.spinnaker.fiat.permissions.RedisPermissionRepositoryConfigProps
import com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository
import com.netflix.spinnaker.fiat.providers.ResourceProvider
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.lock.LockManager
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.boot.actuate.health.Health
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.Callable

class UserRolesSyncerSpec extends Specification {

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME

  @Shared
  Registry registry = new NoopRegistry()

  @Shared
  @AutoCleanup("stop")
  GenericContainer embeddedRedis

  @Shared
  ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)

  @Shared
  JedisPool jedisPool

  @Shared
  Jedis jedis

  @Shared
  RedisPermissionsRepository repo

  def setupSpec() {
    embeddedRedis = new GenericContainer(DockerImageName.parse("redis:5-alpine")).withExposedPorts(6379)
    embeddedRedis.start()
    jedisPool = new JedisPool(embeddedRedis.host, embeddedRedis.getMappedPort(6379))
    jedis = jedisPool.getResource()
    jedis.flushDB()
  }

  def setup() {
    repo = new RedisPermissionsRepository(
        objectMapper,
        new JedisClientDelegate(jedisPool),
        [new Application(), new Account(), new ServiceAccount(), new Role(), new BuildService()],
        new RedisPermissionRepositoryConfigProps(prefix: "unittests"),
        RetryRegistry.ofDefaults()
    )
  }

  def cleanup() {
    jedis.flushDB()
  }

  @Unroll
  def "should update user roles & add service accounts"() {
    setup:
    def extRoleA = new Role("extRoleA").setSource(Role.Source.EXTERNAL)
    def extRoleB = new Role("extRoleB").setSource(Role.Source.EXTERNAL)
    def extRoleC = new Role("extRoleC").setSource(Role.Source.EXTERNAL)
    def user1 = new UserPermission()
        .setId("user1")
        .setAccounts([new Account().setName("account1")] as Set)
        .setRoles([extRoleA] as Set)
    def user2 = new UserPermission()
        .setId("user2")
        .setAccounts([new Account().setName("account2")] as Set)
        .setRoles([extRoleB] as Set)
    def user3 = new UserPermission()
        .setId("user3")
        .setAccounts([new Account().setName("account3")] as Set)
        .setRoles([extRoleC] as Set)
    def unrestrictedUser = new UserPermission()
        .setId(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME)
        .setAccounts([new Account().setName("unrestrictedAccount")] as Set)

    def abcServiceAcct = new UserPermission().setId("abc").setRoles([extRoleC] as Set)
    def xyzServiceAcct = new UserPermission().setId("xyz@domain.com")

    repo.put(user1)
    repo.put(user2)
    repo.put(user3)
    repo.put(unrestrictedUser)

    def newUser2 = new UserPermission()
        .setId("user2")
        .setAccounts([new Account().setName("accountX")] as Set)
        .setRoles([extRoleB] as Set)
    def newUser3 = new UserPermission()
        .setId("user3")
        .setAccounts([new Account().setName("accountX")] as Set)
        .setRoles([extRoleC] as Set)

    def serviceAccountProvider = Mock(ResourceProvider) {
      getAll() >> [new ServiceAccount().setName("abc").setMemberOf(["extRoleC"]),
                   new ServiceAccount().setName("xyz@domain.com")]
    }

    def permissionsResolver = Mock(PermissionsResolver)

    def lockManager = Mock(LockManager) {
      _ * acquireLock() >> { LockManager.LockOptions lockOptions, Callable onLockAcquiredCallback ->
        onLockAcquiredCallback.call()
      }
    }

    @Subject
    def syncer = new UserRolesSyncer(
        new DiscoveryStatusListener(true),
        registry,
        lockManager,
        new UserRolesSyncStrategy.DefaultSynchronizationStrategy(new Synchronizer(new AlwaysUpHealthIndicator(), permissionsResolver, repo, serviceAccountProvider, registry, 1, 1)),
        1,
        1,
        1,
        ""
    )

    expect:
    repo.getAllById() == [
        "user1"       : user1.getRoles() + unrestrictedUser.getRoles(),
        "user2"       : user2.getRoles() + unrestrictedUser.getRoles(),
        "user3"       : user3.getRoles() + unrestrictedUser.getRoles(),
        (UNRESTRICTED): unrestrictedUser.getRoles()
    ]

    when:
    syncer.syncAndReturn(syncRoles)

    then:
    permissionsResolver.resolve(_ as List) >> {
      if (fullsync) {
        ["user1"         : user1,
         "user2"         : newUser2,
         "user3"         : newUser3,
         "abc"           : abcServiceAcct,
         "xyz@domain.com": xyzServiceAcct]
      } else {
        ["user3"         : newUser3,
         "abc"           : abcServiceAcct]
      }
    }
    permissionsResolver.resolveUnrestrictedUser() >> unrestrictedUser

    expect:
    def expectedResult
    if (fullsync) {
      expectedResult = [
              "user1"         : user1.getRoles() + unrestrictedUser.getRoles(),
              "user2"         : newUser2.getRoles() + unrestrictedUser.getRoles(),
              "user3"         : newUser3.getRoles() + unrestrictedUser.getRoles(),
              "abc"           : abcServiceAcct.getRoles() + unrestrictedUser.getRoles(),
              "xyz@domain.com": xyzServiceAcct.getRoles() + unrestrictedUser.getRoles(),
              (UNRESTRICTED)  : unrestrictedUser.getRoles()
      ]
    } else {
      expectedResult = [
              "user1"         : user1.getRoles() + unrestrictedUser.getRoles(),
              "user2"         : user2.getRoles() + unrestrictedUser.getRoles(),
              "user3"         : newUser3.getRoles() + unrestrictedUser.getRoles(),
              "abc"           : abcServiceAcct.getRoles() + unrestrictedUser.getRoles(),
              (UNRESTRICTED)  : unrestrictedUser.getRoles()
      ]
    }
    repo.getAllById() == expectedResult

    where:
    syncRoles    | fullsync
    null         | true
    []           | true
    ["extrolec"] | false
  }

  @Unroll
  def "should only schedule sync when in-service"() {
    given:
    def lockManager = Mock(LockManager)
    def userRolesSyncer = new UserRolesSyncer(
        new DiscoveryStatusListener(discoveryStatusEnabled),
        registry,
        lockManager,
        new UserRolesSyncStrategy.DefaultSynchronizationStrategy(new Synchronizer(new AlwaysUpHealthIndicator(), null, null, null, registry, 1, 1)),
        1,
        1,
        1,
        ""
    )

    when:
    userRolesSyncer.schedule()

    then:
    (shouldAcquireLock ? 1 : 0) * lockManager.acquireLock(_, _)

    where:
    discoveryStatusEnabled                         || shouldAcquireLock
    true                                           || true
    false                                          || false
  }

  class AlwaysUpHealthIndicator extends ResourceProvidersHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
      builder.up()
    }
  }
}
