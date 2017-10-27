/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.index

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.index.config.IndexConfigurationProperties
import com.netflix.kayenta.security.AccountCredentialsRepository
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository
import com.netflix.kayenta.storage.MapBackedStorageServiceRepository
import com.netflix.kayenta.storage.ObjectType
import com.netflix.kayenta.storage.StorageService
import com.netflix.kayenta.storage.StorageServiceRepository
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.*

import static com.netflix.kayenta.index.CanaryConfigIndexingAgent.*

class CanaryConfigIndexingAgentSpec extends Specification {

  static String CURRENT_INSTANCE_ID = "this-kayenta-instance"
  static String OTHER_INSTANCE_ID = "some-other-kayenta-instance"
  static String ACCOUNT_NAME = "some-account"

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  JedisPool jedisPool

  @AutoCleanup
  Jedis jedis

  AccountCredentialsRepository accountCredentialsRepository
  TestNamedAccountCredentials testCredentials
  String mapByApplicationKey
  String pendingUpdatesKey
  StorageService configurationService
  ObjectMapper objectMapper
  CanaryConfigIndex canaryConfigIndex
  // We use the current redis time as a baseline to ensure the entries aren't flushed due to staleness.
  long currentTime
  StorageServiceRepository storageServiceRepository

  @Subject
  CanaryConfigIndexingAgent canaryConfigIndexingAgent

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def setup() {
    jedisPool = embeddedRedis.pool
    jedis = jedisPool.resource
    accountCredentialsRepository = new MapBackedAccountCredentialsRepository()
    testCredentials = new TestNamedAccountCredentials()
    mapByApplicationKey = "kayenta:some-platform:$ACCOUNT_NAME$MAP_BY_APPLICATION_KEY_SUFFIX"
    pendingUpdatesKey = "kayenta:$testCredentials.type:$testCredentials.name$PENDING_UPDATES_KEY_SUFFIX"
    configurationService = Mock(StorageService)
    objectMapper = new ObjectMapper()
    canaryConfigIndex = new CanaryConfigIndex(jedisPool, objectMapper)
    // We use the current redis time as a baseline to ensure entries aren't inadvertently flushed during testing due to staleness.
    currentTime = canaryConfigIndex.getRedisTime()
    storageServiceRepository = new MapBackedStorageServiceRepository(storageServices: [configurationService])
    canaryConfigIndexingAgent = new CanaryConfigIndexingAgent(CURRENT_INSTANCE_ID,
                                                              jedisPool,
                                                              accountCredentialsRepository,
                                                              storageServiceRepository,
                                                              objectMapper,
                                                              canaryConfigIndex,
                                                              new IndexConfigurationProperties())
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  def "agent should not run if lock is not acquired and lock-holding instance has heartbeat"() {
    given:
    jedis.set(INDEXING_INSTANCE_KEY, OTHER_INSTANCE_ID)
    jedis.set("$HEARTBEAT_KEY_PREFIX$OTHER_INSTANCE_ID", "I'm here...")

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    jedis.get(INDEXING_INSTANCE_KEY) == OTHER_INSTANCE_ID
  }

  def "agent should steal lock if lock-holding instance has no heartbeat"() {
    given:
    jedis.set(INDEXING_INSTANCE_KEY, OTHER_INSTANCE_ID)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    !jedis.exists(INDEXING_INSTANCE_KEY)
  }

  def "agent should run if lock is acquired and should set placeholder value since no canary configs are found"() {
    given:
    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, true) >> []
    jedis.hvals(mapByApplicationKey) == [NO_INDEXED_CONFIGS_SENTINEL_VALUE]
  }

  @Unroll
  def "agent should run if lock is acquired and should build index keyed by application; querying for application #applicationToQuery"() {
    given:
    def canaryConfigObjectKeys = [
      buildCanaryConfigSummary("id1", "name1"),
      buildCanaryConfigSummary("id2", "name2")
    ]

    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, true) >> canaryConfigObjectKeys
    1 * configurationService.loadObject(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, "id1") >> new CanaryConfig(applications: scopedApplications["id1"])
    1 * configurationService.loadObject(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, "id2") >> new CanaryConfig(applications: scopedApplications["id2"])
    jedis.hkeys(mapByApplicationKey) == expectedAllApplications as Set
    String canaryConfigSummarySetJson = jedis.hget(mapByApplicationKey, applicationToQuery)
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigSummarySetJson != null ? objectMapper.readValue(canaryConfigSummarySetJson, Set) : []
    canaryConfigSummarySet.collect { it.id } as Set == expectedCanaryConfigIds as Set

    where:
    scopedApplications                      | applicationToQuery || expectedAllApplications | expectedCanaryConfigIds
    [id1: ["a", "b"], id2: ["a"]]           | "a"                || ["a", "b"]              | ["id1", "id2"]
    [id1: ["a", "b"], id2: ["a"]]           | "b"                || ["a", "b"]              | ["id1"]
    [id1: ["a", "b"], id2: ["a", "b"]]      | "b"                || ["a", "b"]              | ["id1", "id2"]
    [id1: ["a", "b"], id2: ["a", "b", "c"]] | "b"                || ["a", "b", "c"]         | ["id1", "id2"]
    [id1: ["a", "b"], id2: ["a", "b", "c"]] | "c"                || ["a", "b", "c"]         | ["id2"]
    [id1: ["a", "b"], id2: ["a", "b", "c"]] | "d"                || ["a", "b", "c"]         | []
  }

  Map buildCanaryConfigSummary(String id, String name) {
    [id: id, name: name, updatedTimestamp: (long)1, updatedTimestampIso: "1"]
  }

  def "agent flushes matching start/finish entries from pending updates queue, while leaving unmatched start entries untouched"() {
    given:
    jedis.rpush(pendingUpdatesKey, "$currentTime:update:start:1:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 1}:update:finish:1")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 2}:update:start:2:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 3}:update:finish:2")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 4}:update:start:3:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 5}:update:start:4:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, true) >> []
    jedis.hvals(mapByApplicationKey) == [NO_INDEXED_CONFIGS_SENTINEL_VALUE]
    jedis.lrange(pendingUpdatesKey, 0, -1) == ["${currentTime + 4}:update:start:3:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}",
                                               "${currentTime + 5}:update:start:4:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}"]
  }

  def "agent flushes matching start/finish entries from pending updates queue and behaves well when the queue ends up completely empty"() {
    given:
    jedis.rpush(pendingUpdatesKey, "$currentTime:update:start:1:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 1}:update:finish:1")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 2}:update:start:2:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 3}:update:finish:2")

    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, true) >> []
    jedis.hvals(mapByApplicationKey) == [NO_INDEXED_CONFIGS_SENTINEL_VALUE]
    jedis.llen(pendingUpdatesKey) == 0
  }

  def "agent flushes matching start/finish entries from pending updates queue, as well as stale start entries, while leaving unmatched/non-stale start entries untouched"() {
    given:
    jedis.rpush(pendingUpdatesKey, "$currentTime:update:start:1:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 1}:update:finish:1")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 2}:update:start:2:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 3}:update:finish:2")
    jedis.rpush(pendingUpdatesKey, "${currentTime - new IndexConfigurationProperties().pendingUpdateStaleEntryThresholdMS}:update:start:5:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 4}:update:start:3:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 5}:update:start:4:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, true) >> []
    jedis.hvals(mapByApplicationKey) == [NO_INDEXED_CONFIGS_SENTINEL_VALUE]
    jedis.lrange(pendingUpdatesKey, 0, -1) == ["${currentTime + 4}:update:start:3:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}",
                                               "${currentTime + 5}:update:start:4:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}"]
  }

  def "agent flushes matching start/finish entries from pending updates queue, as well as stale start entries, and behaves well when the queue ends up completely empty"() {
    given:
    jedis.rpush(pendingUpdatesKey, "$currentTime:update:start:1:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 1}:update:finish:1")
    jedis.rpush(pendingUpdatesKey, "${currentTime - new IndexConfigurationProperties().pendingUpdateStaleEntryThresholdMS}:update:start:6:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 2}:update:start:2:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 3}:update:finish:2")
    jedis.rpush(pendingUpdatesKey, "${currentTime - new IndexConfigurationProperties().pendingUpdateStaleEntryThresholdMS}:update:start:5:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, true) >> []
    jedis.hvals(mapByApplicationKey) == [NO_INDEXED_CONFIGS_SENTINEL_VALUE]
    jedis.llen(pendingUpdatesKey) == 0
  }

  def "agent flushes matching start/finish entries from pending updates queue, while leaving unmatched start entries untouched, and behaves well when in-flight operation fails and open start entry is removed by storage service during indexing window"() {
    given:
    jedis.rpush(pendingUpdatesKey, "$currentTime:update:start:1:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 1}:update:finish:1")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 2}:update:start:2:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 3}:update:finish:2")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 4}:update:start:5:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 5}:update:start:3:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    jedis.rpush(pendingUpdatesKey, "${currentTime + 6}:update:start:4:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    accountCredentialsRepository.save(ACCOUNT_NAME, testCredentials)

    when:
    canaryConfigIndexingAgent.indexCanaryConfigs()

    then:
    1 * configurationService.servicesAccount(ACCOUNT_NAME) >> true

    then:
    // A storage service will remove an open start entry if the operation fails. This test simulates such a failure during the indexing window.
    1 * configurationService.listObjectKeys(ACCOUNT_NAME, ObjectType.CANARY_CONFIG, null, {
      jedis.lrem(pendingUpdatesKey, 1, "${currentTime + 4}:update:start:5:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}") == 1
    }) >> []

    then:
    jedis.hvals(mapByApplicationKey) == [NO_INDEXED_CONFIGS_SENTINEL_VALUE]
    jedis.lrange(pendingUpdatesKey, 0, -1) == ["${currentTime + 5}:update:start:3:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}",
                                               "${currentTime + 6}:update:start:4:{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}"]
  }
}
