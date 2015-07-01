/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool

class JedisPipelineStack implements PipelineStack {

  private JedisPool jedisPool
  private String prefix

  JedisPipelineStack(String prefix, Pool<Jedis> jedisPool) {
    this.jedisPool = jedisPool
    this.prefix = prefix
  }

  boolean addToListIfKeyExists(String id1, String id2, String content) {
    def result = false

    // lua script here ensures that the add and check happens in one atomic operation

    jedisPool.resource.withCloseable { jedis ->
      def script = '''
      local key1 = KEYS[1];
      local key2 = KEYS[2];
      local value = ARGV[1];
      if redis.call('exists', key1) == 1 then
        redis.call('lpush', key2, value);
        return true;
      end
      return false;
      '''
      result = jedis.eval(script, [key(id1), key(id2)], [content])
    }
    result
  }

  void add(String id, String content) {
    jedisPool.resource.withCloseable { jedis ->
      jedis.lpush(key(id), content)
    }
  }

  void remove(String id, String content) {
    jedisPool.resource.withCloseable { jedis ->
      jedis.lrem(key(id), 1, content)
    }
  }

  boolean contains(String id) {
    def result = false
    jedisPool.resource.withCloseable { jedis ->
      result = jedis.exists(key(id))
    }
    result
  }

  List<String> elements(String id) {
    def result = []
    jedisPool.resource.withCloseable { jedis ->
      result = jedis.lrange(key(id), 0, -1)
    }
    result
  }

  private String key(id) {
    "${prefix}:${id}"
  }

}
