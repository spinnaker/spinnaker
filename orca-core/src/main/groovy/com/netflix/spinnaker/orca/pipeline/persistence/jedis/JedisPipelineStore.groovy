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

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStore
import org.springframework.beans.factory.annotation.Autowired
import redis.clients.jedis.JedisCommands

@CompileStatic
class JedisPipelineStore implements PipelineStore {

  private final JedisCommands jedis
  private final ObjectMapper mapper

  @Autowired
  JedisPipelineStore(JedisCommands jedis, ObjectMapper mapper) {
    this.jedis = jedis
    this.mapper = mapper
  }

  @Override
  void store(Pipeline pipeline) {
    if (!pipeline.id) {
      pipeline.id = UUID.randomUUID().toString()
    }

    def key = "pipeline:$pipeline.id"
    jedis.hset(key, "config", mapper.writeValueAsString(pipeline))
  }

  @Override
  Pipeline retrieve(String id) {
    def key = "pipeline:$id"
    def json = jedis.hget(key, "config")
    println mapper.writeValueAsString(mapper.readTree(json))
    mapper.readValue(json, Pipeline)
  }
}
