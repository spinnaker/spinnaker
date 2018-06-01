/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.kork.jedis.locking

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager
import com.netflix.spinnaker.kork.lock.LockManager
import redis.clients.jedis.JedisPool
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.util.concurrent.Callable

import static com.netflix.spinnaker.kork.lock.LockManager.LockStatus.*
import static com.netflix.spinnaker.kork.lock.RefreshableLockManager.*

class RedisLockManagerSpec extends Specification {
  @Shared def embeddedRedis = EmbeddedRedis.embed()
  def jedisPool = embeddedRedis.getPool() as JedisPool
  def objectMapper = new ObjectMapper()
  def clock = Clock.systemDefaultZone()
  def registry = new NoopRegistry()
  def redisClientDelegate = new JedisClientDelegate(jedisPool)
  def heartbeatRateMillis = 30L
  def testLockMaxDurationMillis = 30L
  def redisLockManager = new RedisLockManager(
    "testOwner",
    clock,
    registry,
    objectMapper,
    redisClientDelegate,
    Optional.of(heartbeatRateMillis),
    Optional.of(testLockMaxDurationMillis)
  )

  def setup() {
    jedisPool.resource.flushDB()
  }

  def cleanupSpec() {
    embeddedRedis.destroy()
  }

  def "should acquire a simple lock and auto release"() {
    when:
    def result = redisLockManager.acquireLock("veryImportantLock", testLockMaxDurationMillis, {
      return "Run after lock is safely acquired."
    } as Callable<String>)

    then:
    result.lockStatus == ACQUIRED
    result.released == true
    result.onLockAcquiredCallbackResult == "Run after lock is safely acquired."
  }

  def "should store arbitrary data as attributes alongside the lock"() {
    given:
    def lockOptions = new LockManager.LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")
      .withAttributes(["key:value","key2:value2"])

    when:
    def result = redisLockManager.acquireLock(lockOptions, {
      return "Lock with data in attributes"
    } as Callable<String>)

    then:
    result.lock.attributes == "key:value;key2:value2"
  }

  def "should fail to acquire an already taken lock"() {
    given:
    def lockOptions = new LockManager.LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    and:
    redisLockManager.tryCreateLock(lockOptions)

    when:
    def result = redisLockManager.acquireLock(lockOptions, {
      return "attempting to acquire lock"
    } as Callable<String>)

    then:
    result.lockStatus == TAKEN
    result.onLockAcquiredCallbackResult == null
  }

  def "should acquire with heartbeat"() {
    given:
    def lockOptions = new LockManager.LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    when:
    def result = redisLockManager.acquireLock(lockOptions, {
      // simulates long running task
      Thread.sleep(100)
      "Done"
    } as Callable<String>)

    then:
    result.released == true
    result.lockStatus == ACQUIRED
    result.onLockAcquiredCallbackResult == "Done"

    when:
    result = redisLockManager.acquireLock(lockOptions.withLockName("withRunnable"), {
      // simulates long running task
      Thread.sleep(100)
    } as Runnable)

    then:
    result.onLockAcquiredCallbackResult == null
    result.lockStatus == ACQUIRED
  }

  def "should propagate exception on callback failure"() {
    given:
    def lockName = "veryImportantLock"
    def onLockAcquiredCallback = {
      throw new IllegalStateException("Failure")
    }

    when:
    redisLockManager.acquireLock(lockName, testLockMaxDurationMillis, onLockAcquiredCallback)

    then:
    thrown(LockManager.LockCallbackException)
  }

  def "should release a lock"() {
    given:
    def lockOptions = new LockManager.LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    and:
    def lock = redisLockManager.tryCreateLock(lockOptions)

    when:
    def response = redisLockManager.tryReleaseLock(lock)

    then:
    response == LockManager.LockReleaseStatus.SUCCESS.toString()
  }

  def "should heartbeat by updating lock ttl"() {
    given:
    def lockOptions = new LockManager.LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    and:
    def lock = redisLockManager.tryCreateLock(lockOptions)
    def request = new HeartbeatLockRequest(lock, clock, Duration.ofMillis(200))
    Thread.sleep(10)

    when:
    def response = redisLockManager.heartbeat(request)
    Thread.sleep(10)

    then:
    response.lockStatus == ACQUIRED

    when: "Late heartbeat resulting in expired lock "
    request = new HeartbeatLockRequest(lock, clock, Duration.ofMillis(200))
    redisLockManager.heartbeat(request)
    Thread.sleep(30)
    response = redisLockManager.heartbeat(request)

    then:
    response.lockStatus == EXPIRED
  }
}
