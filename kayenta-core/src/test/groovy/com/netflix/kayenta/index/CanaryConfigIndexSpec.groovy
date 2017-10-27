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
import com.netflix.kayenta.index.config.CanaryConfigIndexAction
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.*

import static com.netflix.kayenta.index.CanaryConfigIndexingAgent.*

class CanaryConfigIndexSpec extends Specification {

  static String CURRENT_INSTANCE_ID = "this-kayenta-instance"
  static String ACCOUNT_NAME = "some-account"

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  JedisPool jedisPool

  @AutoCleanup
  Jedis jedis

  TestNamedAccountCredentials testCredentials
  String mapByApplicationKey
  ObjectMapper objectMapper
  // We use the current redis time as a baseline to ensure the entries aren't flushed due to staleness.
  long currentTime

  @Subject
  CanaryConfigIndex canaryConfigIndex

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def setup() {
    jedisPool = embeddedRedis.pool
    jedis = jedisPool.resource
    testCredentials = new TestNamedAccountCredentials()
    mapByApplicationKey = "kayenta:some-platform:$ACCOUNT_NAME$MAP_BY_APPLICATION_KEY_SUFFIX"
    objectMapper = new ObjectMapper()
    canaryConfigIndex = new CanaryConfigIndex(jedisPool, objectMapper)
    // We use the current redis time as a baseline to ensure entries aren't inadvertently flushed during testing due to staleness.
    currentTime = canaryConfigIndex.getRedisTime()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  @Unroll
  def "all canary configs are returned when no applications are specified"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, applicationList)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2"] as Set

    where:
    applicationList << [null, []]
  }

  def "correct canary configs are returned when applications are specified"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["c"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == [] as Set
  }

  @Unroll
  def "no canary configs are returned when the index is empty"() {
    given:
    jedis.hset(mapByApplicationKey, "not-a-real-application:" + CURRENT_INSTANCE_ID, NO_INDEXED_CONFIGS_SENTINEL_VALUE)

    expect:
    canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, applicationList) == [] as Set

    where:
    applicationList << [null, [], ["a"], ["a", "b"]]
  }

  @Unroll
  def "exception is thrown when the index is not ready"() {
    when:
    canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, applicationList)

    then:
    thrown IllegalArgumentException

    where:
    applicationList << [null, [], ["a"], ["a", "b"]]
  }

  def "populated canary config summary list respects pending updates"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "1", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id4"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id3"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 2 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set
  }

  @Unroll
  def "rename in pending updates queue is reflected in canary config summary list"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, applicationList)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2"] as Set
    canaryConfigSummarySet.find { it.id == "id1" }.name == "name1"

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "3", "{\"id\":\"id1\",\"name\":\"different-name\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, applicationList)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2"] as Set
    canaryConfigSummarySet.find { it.id == "id1" }.name == "different-name"

    where:
    applicationList << [null, [], ["b"]]
  }

  def "changing application scoping of a given canary config in pending updates queue is reflected in canary config summary list"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["c"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == [] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "3", "{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"c\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id2"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["c"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "3", "{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"c\", \"d\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id2"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["c"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["d"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set
  }

  def "update and delete actions in pending updates queue are idempotent w.r.t. building canary config summary list"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "1", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id4"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id4"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id3"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "1", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigIndex.finishPendingUpdate(testCredentials, CanaryConfigIndexAction.UPDATE, "2")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigIndex.finishPendingUpdate(testCredentials, CanaryConfigIndexAction.UPDATE, "2")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigIndex.finishPendingUpdate(testCredentials, CanaryConfigIndexAction.UPDATE, "2")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id4"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id3"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigIndex.finishPendingUpdate(testCredentials, CanaryConfigIndexAction.DELETE, "3")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigIndex.finishPendingUpdate(testCredentials, CanaryConfigIndexAction.DELETE, "3")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigIndex.finishPendingUpdate(testCredentials, CanaryConfigIndexAction.DELETE, "3")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["a"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1"] as Set

    when:
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, ["b"])

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id4"] as Set
  }

  @Unroll
  def "canary config summary list includes union of application-scoped canary configs"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\", \"c\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}," +
                                          "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\", \"c\"]}]")
    jedis.hset(mapByApplicationKey, "c", "[{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\", \"c\"]}]")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, applicationList)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == expectedCanaryConfigIds as Set

    where:
    applicationList || expectedCanaryConfigIds
    null            || ["id1", "id2", "id3"]
    []              || ["id1", "id2", "id3"]
    ["a"]           || ["id1", "id3"]
    ["b"]           || ["id1", "id2", "id3"]
    ["c"]           || ["id3"]
    ["a", "b"]      || ["id1", "id2", "id3"]
    ["b", "c"]      || ["id1", "id2", "id3"]
    ["a", "c"]      || ["id1", "id3"]
    ["a", "b", "c"] || ["id1", "id2", "id3"]
  }

  def "populated canary config summary list respects pending updates, and removing failed pending updates unwinds those changes"() {
    given:
    jedis.hset(mapByApplicationKey, "a", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}]")
    jedis.hset(mapByApplicationKey, "b", "[{\"id\":\"id1\",\"name\":\"name1\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\", \"b\"]}," +
                                          "{\"id\":\"id2\",\"name\":\"name2\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}]")
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + "", CanaryConfigIndexAction.UPDATE, "1", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")

    when:
    Set<Map<String, Object>> canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id4"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 2 + "", CanaryConfigIndexAction.UPDATE, "3", "{\"id\":\"id5\",\"name\":\"name5\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id4", "id5"] as Set

    when:
    canaryConfigIndex.removeFailedPendingUpdate(testCredentials, currentTime + 1 + "", CanaryConfigIndexAction.UPDATE, "2", "{\"id\":\"id4\",\"name\":\"name4\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"b\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id5"] as Set

    when:
    canaryConfigIndex.startPendingUpdate(testCredentials, currentTime + 2 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id5"] as Set

    when:
    canaryConfigIndex.removeFailedPendingUpdate(testCredentials, currentTime + 2 + "", CanaryConfigIndexAction.DELETE, "3", "{\"id\":\"id3\",\"name\":\"name3\",\"updatedTimestamp\":1507570197295,\"updatedTimestampIso\":\"2017-10-09T15:07:23.677Z\",\"applications\":[\"a\"]}")
    canaryConfigSummarySet = canaryConfigIndex.getCanaryConfigSummarySet(testCredentials, null)

    then:
    canaryConfigSummarySet.collect { it.id } as Set == ["id1", "id2", "id3", "id5"] as Set
  }
}
