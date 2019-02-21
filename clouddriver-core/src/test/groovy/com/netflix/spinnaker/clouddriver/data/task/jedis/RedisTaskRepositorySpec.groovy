/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.data.task.jedis


import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RedisTaskRepositorySpec extends Specification {

  @Shared
  RedisTaskRepository taskRepository

  @Shared
  JedisPool jedisPool

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    jedisPool = embeddedRedis.pool as JedisPool
    taskRepository = new RedisTaskRepository(new JedisClientDelegate(jedisPool), Optional.empty())
  }

  def setup() {
    jedisPool.resource.withCloseable {
      ((Jedis) it).flushDB()
    }
  }

  void "reads from previous redis if task missing"() {
    given:
    def embeddedRedis1 = EmbeddedRedis.embed()
    def previousJedisPool = embeddedRedis1.pool as JedisPool
    previousJedisPool.resource.withCloseable { it.flushDB() }

    def embeddedRedis2 = EmbeddedRedis.embed()
    def currentPool = embeddedRedis2.pool as JedisPool
    currentPool.resource.withCloseable { it.flushDB() }

    def taskR = new RedisTaskRepository(new JedisClientDelegate(previousJedisPool), Optional.empty())
    def oldPoolTask = taskR.create("starting", "foo")
    oldPoolTask.complete()

    def newTaskR = new RedisTaskRepository(new JedisClientDelegate(currentPool), Optional.of(new JedisClientDelegate(previousJedisPool)))

    when:
    def fromOldPool = newTaskR.get(oldPoolTask.id)

    then:
    fromOldPool.id == oldPoolTask.id
    fromOldPool.startTimeMs == oldPoolTask.startTimeMs
    fromOldPool.status.status == oldPoolTask.status.status

    cleanup:
    embeddedRedis1.destroy()
    embeddedRedis2.destroy()
  }
}
