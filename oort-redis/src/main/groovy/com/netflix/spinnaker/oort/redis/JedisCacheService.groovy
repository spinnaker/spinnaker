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

package com.netflix.spinnaker.oort.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.oort.model.CacheService
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisDataException

@Log4j
class JedisCacheService implements CacheService {

  @Value('${redis.cache.key:CACHE}')
  String cacheKey

  @Autowired
  JedisPool jedisPool

  @Autowired
  @Qualifier('amazonObjectMapper')
  ObjectMapper objectMapper

  @Override
  public <T> T retrieve(String key, Class<T> klazz) {
    def json = withJedis {
      hget cacheKey, key
    }
    json ? objectMapper.readValue(json, klazz) : null
  }

  @Override
  boolean put(String key, Object object) {
    withJedis {
      hset cacheKey, key, objectMapper.writeValueAsString(object)
    }
  }

  @Override
  void free(String key) {
    withJedis {
      hdel cacheKey, key
    }
  }

  @Override
  boolean exists(String key) {
    withJedis {
      hexists cacheKey, key
    }
  }

  @Override
  Set<String> keys() {
    withJedis {
      hkeys cacheKey
    }
  }

  private <T> T withJedis(@DelegatesTo(Jedis) Closure<T> closure) {
    def jedis = jedisPool.resource
    try {
      closure.delegate = jedis
      return closure.call()
    } catch (JedisDataException e) {
      log.error e
      jedisPool.returnBrokenResource jedis
    } finally {
      jedisPool.returnResource jedis
    }
    null
  }

}
