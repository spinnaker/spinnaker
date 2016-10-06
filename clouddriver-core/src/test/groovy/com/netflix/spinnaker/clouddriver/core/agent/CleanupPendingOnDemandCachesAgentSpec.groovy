/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.clouddriver.core.agent

import com.netflix.spinnaker.cats.redis.JedisPoolSource
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import org.springframework.context.ApplicationContext
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CleanupPendingOnDemandCachesAgentSpec extends Specification {
  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis = EmbeddedRedis.embed()

  def jedisSource = new JedisPoolSource(embeddedRedis.pool as JedisPool)

  def "should cleanup onDemand:members set for a provider"() {
    given:
    def agent = new CleanupPendingOnDemandCachesAgent(jedisSource, Stub(ApplicationContext))
    def providers = [
        new CoreProvider([])
    ]
    jedisSource.jedis.withCloseable { Jedis jedis ->
      // two keys in set
      jedis.sadd(CoreProvider.name + ":onDemand:members", "does-not-exist")
      jedis.sadd(CoreProvider.name + ":onDemand:members", "exists")

      // only one key exists
      jedis.set(CoreProvider.name + ":onDemand:attributes:exists", "this value exists!")
    }

    when:
    agent.run(providers)

    then:
    jedisSource.jedis.withCloseable { Jedis jedis ->
      // only the key that exists should remain in the set
      (jedis.smembers(CoreProvider.name + ":onDemand:members") as List<String>) == ["exists"]
    }

  }
}
