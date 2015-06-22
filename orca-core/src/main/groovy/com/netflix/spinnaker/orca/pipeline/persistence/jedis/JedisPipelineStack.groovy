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
import redis.clients.jedis.JedisCommands

class JedisPipelineStack implements PipelineStack{

  private JedisCommands jedis
  private String prefix

  JedisPipelineStack(String prefix, JedisCommands jedis) {
    this.jedis = jedis
    this.prefix = prefix
  }

  void add(String id, String content) {
    jedis.lpush(key(id), content)
  }

  void remove(String id, String content) {
    jedis.lrem(key(id), 1, content)
  }

  boolean contains(String id) {
    jedis.exists(key(id))
  }

  List<String> elements(String id){
    jedis.lrange(key(id), 0, -1)
  }

  private String key(id) {
    "${prefix}:${id}"
  }

}
