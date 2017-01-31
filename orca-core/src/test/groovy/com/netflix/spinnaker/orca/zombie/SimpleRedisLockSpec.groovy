/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.zombie

import java.time.Duration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.functions.Action1
import rx.functions.Func0
import spock.lang.Specification
import spock.lang.Subject
import static java.util.UUID.randomUUID
import static rx.Observable.empty
import static rx.Observable.just

class SimpleRedisLockSpec extends Specification {

  def redis = Mock(Jedis)
  def redisPool = Stub(Pool) {
    getResource() >> redis
  }

  def key = getClass().simpleName
  def ttl = Duration.ofHours(1)

  @Subject lock = new SimpleRedisLock(
    redisPool, key, ttl
  )

  def uniqueId = randomUUID().toString()
  def generator = Mock(Func0)
  def subscriber = Mock(Action1)

  def "does nothing if lock cannot be acquired"() {
    given:
    redis.setnx(*_) >> 0

    when:
    lock.withLock(uniqueId, generator, subscriber)

    then:
    0 * generator.call()
    0 * subscriber.call(_)

    and:
    0 * redis.expire(*_)
    0 * redis.del(_)
  }

  def "runs routine and releases lock"() {
    given:
    redis.setnx(key, uniqueId) >> 1L
    redis.get(key) >> uniqueId

    and:
    generator.call() >> just(1, 2)

    when:
    lock.withLock(uniqueId, generator, subscriber)

    then:
    1 * redis.expire(key, ttl.toMillis() / 1000i)

    then:
    1 * subscriber.call(1)
    1 * subscriber.call(2)

    then:
    1 * redis.del(key)
  }

  def "does not release non-matching lock"() {
    given:
    redis.setnx(key, uniqueId) >> 1L
    redis.get(key) >> randomUUID().toString()

    and:
    generator.call() >> just(1)

    when:
    lock.withLock(uniqueId, generator, subscriber)

    then:
    0 * redis.del(key)
  }

  def "releases lock if no action taken"() {
    given:
    redis.setnx(key, uniqueId) >> 1L
    redis.get(key) >> uniqueId

    and:
    generator.call() >> empty()

    when:
    lock.withLock(uniqueId, generator, subscriber)

    then:
    0 * subscriber.call(_)

    and:
    1 * redis.del(key)
  }

  def "releases lock if generator errors"() {
    given:
    redis.setnx(key, uniqueId) >> 1L
    redis.get(key) >> uniqueId

    and:
    generator.call() >> just(1).map { throw new RuntimeException() }

    when:
    lock.withLock(uniqueId, generator, subscriber)

    then:
    1 * redis.del(key)
  }

  def "releases lock if subscriber errors"() {
    given:
    redis.setnx(key, uniqueId) >> 1L
    redis.get(key) >> uniqueId

    and:
    generator.call() >> just(1)

    and:
    subscriber.call(_) >> { throw new RuntimeException() }

    when:
    lock.withLock(uniqueId, generator, subscriber)

    then:
    1 * redis.del(key)
  }

}
