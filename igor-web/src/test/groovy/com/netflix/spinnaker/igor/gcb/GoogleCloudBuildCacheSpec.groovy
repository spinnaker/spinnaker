/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.cloudbuild.v1.model.Build
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

class GoogleCloudBuildCacheSpec extends Specification {
  @Shared LockService lockService = Stub(LockService) {
    acquire(_ as String, _ as Duration, _ as Runnable ) >> { String lockName, Duration duration , Runnable runnable ->
      runnable.run()
    }
  }
  @Shared EmbeddedRedis redis = EmbeddedRedis.embed()
  @Shared RedisClientDelegate redisClientDelegate = new JedisClientDelegate(redis.getPool())
  String keyPrefix = "abc"
  String lockPrefix = "def"
  @Shared GoogleCloudBuildCache googleCloudBuildCache = new GoogleCloudBuildCache(lockService, redisClientDelegate, keyPrefix, lockPrefix)
  @Shared ObjectMapper objectMapper = new ObjectMapper()

  def cleanupSpec() {
    redis.destroy()
  }

  def "properly stores a build"() {
    given:
    def buildId = "d7dd57a6-5a31-415c-a360-8ed083fdd837"
    def status = "QUEUED"
    def build = getBuild(buildId, status)
    googleCloudBuildCache.updateBuild(buildId, status, build)

    expect:
    googleCloudBuildCache.getBuild(buildId) == build
  }

  def "only supersedes builds with newer information"() {
    given:
    def buildId = "1e87c466-1311-4480-bc38-cd4b759e378b"
    def firstBuild = getBuild(buildId, firstStatus)
    def secondBuild = getBuild(buildId, secondStatus)
    googleCloudBuildCache.updateBuild(buildId, firstStatus, firstBuild)
    googleCloudBuildCache.updateBuild(buildId, secondStatus, secondBuild)

    expect:
    googleCloudBuildCache.getBuild(buildId) == (shouldUpdate ? secondBuild : firstBuild)

    where:
    firstStatus  | secondStatus | shouldUpdate
    "QUEUED"     | "WORKING"    | true
    "WORKING"    | "QUEUED"     | false
    "QUEUED"     | "SUCCESS"    | true
    "SUCCESS"    | "QUEUED"     | false
    "QUEUED"     | "SUCCESS"    | true
    "SUCCESS"    | "QUEUED"     | false
    "SUCCESS"    | "FAILURE"    | true
    "FAILURE"    | "SUCCESS"    | true
  }

  def "updates are always allowed with unknown statuses"() {
    given:
    def buildId = "43a14e66-ee70-4c5c-85f3-a6f822429b7d"
    def firstBuild = getBuild(buildId, firstStatus)
    def secondBuild = getBuild(buildId, secondStatus)
    googleCloudBuildCache.updateBuild(buildId, firstStatus, firstBuild)
    googleCloudBuildCache.updateBuild(buildId, secondStatus, secondBuild)

    expect:
    googleCloudBuildCache.getBuild(buildId) == (shouldUpdate ? secondBuild : firstBuild)

    where:
    firstStatus  | secondStatus | shouldUpdate
    "AAA"        | "BBB"        | true
    "WORKING"    | "BBB"        | true
    "BBB"        | "WORKING"    | true
  }

  private String getBuild(String id, String status) {
    return objectMapper.writeValueAsString(new Build().setId(id).setStatus(status))
  }

}
