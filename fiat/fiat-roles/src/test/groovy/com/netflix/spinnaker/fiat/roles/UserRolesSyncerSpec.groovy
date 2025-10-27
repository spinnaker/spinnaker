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
import com.netflix.spinnaker.fiat.config.SyncConfig
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.model.resources.Application
import com.netflix.spinnaker.fiat.model.resources.BuildService
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import com.netflix.spinnaker.fiat.permissions.PermissionResolutionException
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver
import com.netflix.spinnaker.fiat.permissions.RedisPermissionRepositoryConfigProps
import com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository
import com.netflix.spinnaker.fiat.providers.ResourceProvider
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager
import com.netflix.spinnaker.kork.lock.LockManager
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.boot.actuate.health.Health
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.time.Clock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.function.Consumer

class UserRolesSyncerSpec extends Specification {

  private static final String UNRESTRICTED = UnrestrictedResourceConfig.UNRESTRICTED_USERNAME

  @Shared
  Registry registry = new NoopRegistry()

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)

  @Shared
  Jedis jedis

  @Shared
  RedisPermissionsRepository repo

  LockManager lockManager

  SyncConfig syncConfigProperties = new SyncConfig()

  RedisClientDelegate redisClientDelegate

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    jedis = embeddedRedis.jedis
    jedis.flushDB()
  }

  def setup() {
    redisClientDelegate = new JedisClientDelegate(embeddedRedis.pool as JedisPool)
    repo = new RedisPermissionsRepository(
        objectMapper,
        redisClientDelegate,
        [new Application(), new Account(), new ServiceAccount(), new Role(), new BuildService()],
        new RedisPermissionRepositoryConfigProps(prefix: "unittests"),
        RetryRegistry.ofDefaults()
    )

    lockManager = new RedisLockManager(
            null, // will fall back to running node name
            Clock.systemDefaultZone(),
            registry,
            objectMapper,
            new JedisClientDelegate(embeddedRedis.pool as JedisPool),
            Optional.empty(),
            Optional.empty())
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

    @Subject
    def syncer = new UserRolesSyncer(
        new DiscoveryStatusListener(true),
        registry,
        new UserRolesSyncStrategy.DefaultSynchronizationStrategy(new Synchronizer(new AlwaysUpHealthIndicator(), permissionsResolver, repo, serviceAccountProvider, registry, redisClientDelegate, syncConfigProperties, lockManager)),
        syncConfigProperties
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
    def permissionsResolver = Mock(PermissionsResolver) {
      resolveUnrestrictedUser() >> new UserPermission().setId(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME)
    }
    def serviceAccountProvider = Mock(ResourceProvider) {
      getAll() >> []
    }
    def lockManager = Mock(LockManager) {
      _ * acquireLock() >> { LockManager.LockOptions lockOptions, Callable onLockAcquiredCallback ->
        return new LockManager.AcquireLockResponse<>(null, onLockAcquiredCallback.call(), LockManager.LockStatus.ACQUIRED, new RuntimeException(), true);
      }
    }
    redisClientDelegate.withCommandsClient({ c ->
      c.set("spinnaker:fiat:user_roles:last_sync_time", (System.currentTimeMillis() + 10000).toString())
      c.set("spinnaker:fiat:user_roles:count", String.valueOf(1));
    } as Consumer)

    def userRolesSyncer = new UserRolesSyncer(
        new DiscoveryStatusListener(discoveryStatusEnabled),
        registry,
        new UserRolesSyncStrategy.DefaultSynchronizationStrategy(new Synchronizer(new AlwaysUpHealthIndicator(), permissionsResolver, repo, serviceAccountProvider, registry, redisClientDelegate, syncConfigProperties, lockManager)),
        syncConfigProperties
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

  @Unroll
  def "syncServiceAccounts adds service accounts and updates relevant users"() {
    setup:
    def extRoleA = new Role("extrolea").setSource(Role.Source.EXTERNAL)
    def extRoleB = new Role("extroleb").setSource(Role.Source.EXTERNAL)
    def extRoleC = new Role("extrolec").setSource(Role.Source.EXTERNAL)
    def abcResource = new ServiceAccount().setName("abc@managed-service-account").setMemberOf(["extroleb"])

    def user1 = new UserPermission()
            .setId("user1")
            .setAccounts([new Account().setName("account1")] as Set)
            .setRoles([extRoleA, extRoleC] as Set)
    def user2 = new UserPermission()
            .setId("user2")
            .setAccounts([new Account().setName("account2")] as Set)
            .setRoles([extRoleA] as Set)
            .addResources([abcResource])
    def user3 = new UserPermission()
            .setId("user3")
            .setAccounts([new Account().setName("account3")] as Set)
            .setRoles([extRoleB, extRoleC] as Set)
            .addResources([abcResource])
    def unrestrictedUser = new UserPermission()
            .setId(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME)
            .setAccounts([new Account().setName("unrestrictedAccount")] as Set)
    def abcServiceAcct = new UserPermission()
            .setId("abc@managed-service-account")
            .setRoles([extRoleB, extRoleC] as Set)
            .addResources([abcResource])
    def newServiceAccountResource = new ServiceAccount().setName("new@managed-service-account").setMemberOf([extRoleB.name, extRoleC.name])
    def newServiceAcct = new UserPermission()
            .setId("new@managed-service-account")
            .setRoles([extRoleB, extRoleC] as Set)
            .addResources([abcResource, newServiceAccountResource])

    def allUsers = [user1, user2, user3, abcServiceAcct, newServiceAcct, unrestrictedUser]
    (allUsers - [newServiceAcct]).forEach({it -> repo.put(it)})

    def serviceAccountProvider = Mock(ResourceProvider)
    def permissionsResolver = Mock(PermissionsResolver) { resolver ->
      resolver.resolveAndMerge(new ExternalUser().setId("new@managed-service-account").setExternalRoles([extRoleB, extRoleC])) >> newServiceAcct
      Map<String, Collection<Role>> userRoles = (allUsers - [user1, user2, unrestrictedUser]).collectEntries {[it.id, it.roles]}
      resolver.resolveResources(userRoles) >>
              (allUsers - [user1, user2, unrestrictedUser]).collectEntries { [it.id, it.addResources([newServiceAccountResource])]}
    }

    def lockManager = Mock(LockManager) {
      _ * acquireLock() >> { LockManager.LockOptions lockOptions, Callable onLockAcquiredCallback ->
        onLockAcquiredCallback.call()
      }
    }

    @Subject
    def syncer = new UserRolesSyncer(
            new DiscoveryStatusListener(false),
            registry,
            new UserRolesSyncStrategy.DefaultSynchronizationStrategy(
                    new Synchronizer(
                            new AlwaysUpHealthIndicator(),
                            permissionsResolver,
                            repo,
                            serviceAccountProvider,
                            registry,
                            redisClientDelegate,
                            syncConfigProperties,
                            lockManager)),
            syncConfigProperties
    )

    expect:
    repo.getAllByRoles([extRoleB.name, extRoleC.name]) == (allUsers - [user2, newServiceAcct]).collectEntries { [ it.id, it.roles]}

    when:
    syncer.syncServiceAccount("new@managed-service-account", [extRoleB.name, extRoleC.name])

    then:
    permissionsResolver.resolveUnrestrictedUser() >> unrestrictedUser

    expect:
    repo.getAllById() == allUsers.collectEntries { [ it.id, it.roles]}
    [user1, user2, user3, newServiceAcct, abcServiceAcct, unrestrictedUser]
            .forEach({ it -> repo.get(it.getId()).get() == it.merge(unrestrictedUser) })
  }

  @Unroll
  def "should update user roles & add service accounts when invoked by concurrent requests"() {
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

    def extUsers
    def expectedResolveOutput
    def expectedResult
    if (syncRoles == null || syncRoles.isEmpty()) {
      extUsers = [
              new ExternalUser()
                      .setId(user1.id)
                      .setExternalRoles([new Role("extrolea").setSource(Role.Source.EXTERNAL)] as List<Role>),
              new ExternalUser()
                      .setId(user2.id)
                      .setExternalRoles([new Role("extroleb").setSource(Role.Source.EXTERNAL)] as List<Role>),
              new ExternalUser()
                      .setId(abcServiceAcct.id)
                      .setExternalRoles([new Role("extrolec").setSource(Role.Source.EXTERNAL)] as List<Role>),
              new ExternalUser()
                      .setId(xyzServiceAcct.id)
                      .setExternalRoles([]),
              new ExternalUser()
                      .setId(user3.id)
                      .setExternalRoles([new Role("extrolec").setSource(Role.Source.EXTERNAL)] as List<Role>)
      ]

      expectedResolveOutput = [
              "user1"         : user1,
              "user2"         : newUser2,
              "user3"         : newUser3,
              "abc"           : abcServiceAcct,
              "xyz@domain.com": xyzServiceAcct
      ]

      expectedResult = [
              "user1"         : user1.getRoles() + unrestrictedUser.getRoles(),
              "user2"         : newUser2.getRoles() + unrestrictedUser.getRoles(),
              "user3"         : newUser3.getRoles() + unrestrictedUser.getRoles(),
              "abc"           : abcServiceAcct.getRoles() + unrestrictedUser.getRoles(),
              "xyz@domain.com": xyzServiceAcct.getRoles() + unrestrictedUser.getRoles(),
              (UNRESTRICTED)  : unrestrictedUser.getRoles()
      ]
    } else {
      extUsers =  [
              new ExternalUser()
                      .setId(abcServiceAcct.id)
                      .setExternalRoles([new Role("extrolec").setSource(Role.Source.EXTERNAL)] as List<Role>),
              new ExternalUser()
                      .setId(user3.id)
                      .setExternalRoles([new Role("extrolec").setSource(Role.Source.EXTERNAL)] as List<Role>)
      ]
      expectedResolveOutput = [
              "user3"         : newUser3,
              "abc"           : abcServiceAcct
      ]

      expectedResult = [
              "user1"         : user1.getRoles() + unrestrictedUser.getRoles(),
              "user2"         : user2.getRoles() + unrestrictedUser.getRoles(),
              "user3"         : newUser3.getRoles() + unrestrictedUser.getRoles(),
              "abc"           : abcServiceAcct.getRoles() + unrestrictedUser.getRoles(),
              (UNRESTRICTED)  : unrestrictedUser.getRoles()
      ]
    }


    @Subject
    def syncer = new UserRolesSyncer(
            new DiscoveryStatusListener(true),
            registry,
            new UserRolesSyncStrategy.DefaultSynchronizationStrategy(new Synchronizer(new AlwaysUpHealthIndicator(), permissionsResolver, repo, serviceAccountProvider, registry, redisClientDelegate, syncConfigProperties, lockManager)),
            syncConfigProperties
    )

    expect:
    repo.getAllById() == [
            "user1"       : user1.getRoles() + unrestrictedUser.getRoles(),
            "user2"       : user2.getRoles() + unrestrictedUser.getRoles(),
            "user3"       : user3.getRoles() + unrestrictedUser.getRoles(),
            (UNRESTRICTED): unrestrictedUser.getRoles()
    ]

    when:
    def results = new ArrayList(10)
    def futures = new ArrayList(10)

    def threadPool = Executors.newFixedThreadPool(10)
    try {
      10.times {
        futures.add(threadPool.submit({ ->
          syncer.syncAndReturn(syncRoles)
        } as Callable))
      }
      futures.each {results.add(it.get())}
    } finally {
      threadPool.shutdown()
    }

    then:
    permissionsResolver.resolve(extUsers) >> expectedResolveOutput
    permissionsResolver.resolveUnrestrictedUser() >> unrestrictedUser

    expect:
    repo.getAllById() == expectedResult

    results.each {
      assert it == extUsers.size()
    }

    where:
    syncRoles << [null, [], ["extrolec"]]

  }


  @Unroll
  def "should handle exceptions when handling concurrent requests"() {
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

    repo.put(user1)
    repo.put(user2)
    repo.put(user3)
    repo.put(unrestrictedUser)

    def serviceAccountProvider = Mock(ResourceProvider) {
      getAll() >> [new ServiceAccount().setName("abc").setMemberOf(["extRoleC"]),
                   new ServiceAccount().setName("xyz@domain.com")]
    }

    def permissionsResolver = Mock(PermissionsResolver)

    syncConfigProperties.setSyncDelayTimeoutMs(50)

    @Subject
    def syncer = new UserRolesSyncer(
            new DiscoveryStatusListener(true),
            registry,
            new UserRolesSyncStrategy.DefaultSynchronizationStrategy(new Synchronizer(new AlwaysUpHealthIndicator(), permissionsResolver, repo, serviceAccountProvider, registry, redisClientDelegate, syncConfigProperties, lockManager)),
            syncConfigProperties
    )

    expect:
    repo.getAllById() == [
            "user1"       : user1.getRoles() + unrestrictedUser.getRoles(),
            "user2"       : user2.getRoles() + unrestrictedUser.getRoles(),
            "user3"       : user3.getRoles() + unrestrictedUser.getRoles(),
            (UNRESTRICTED): unrestrictedUser.getRoles()
    ]

    when:
    def results = new ArrayList(2)
    def futures = new ArrayList(2)

    def threadPool = Executors.newFixedThreadPool(2)
    try {
      2.times {
        futures.add(threadPool.submit({ ->
          syncer.syncAndReturn(syncRoles)
        } as Callable))
      }
      futures.each {results.add(it.get())}
    } finally {
      threadPool.shutdown()
    }

    then:
    permissionsResolver.resolve(_ as List) >> {
      throw new PermissionResolutionException("permission resolution failed from provider")
    }
    permissionsResolver.resolveUnrestrictedUser() >> unrestrictedUser

    expect:
    results.each {
      assert it == 0
    }

    where:
    syncRoles  << [["extrolec"]]
  }

  class AlwaysUpHealthIndicator extends ResourceProvidersHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
      builder.up()
    }
  }
}
