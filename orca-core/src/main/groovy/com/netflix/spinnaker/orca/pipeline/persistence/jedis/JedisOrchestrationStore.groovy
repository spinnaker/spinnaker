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

package com.netflix.spinnaker.orca.pipeline.persistence.jedis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import groovy.transform.CompileStatic
import redis.clients.jedis.JedisCommands

class JedisOrchestrationStore extends AbstractJedisBackedExecutionStore<Orchestration> {
  JedisOrchestrationStore(JedisCommands jedis, ObjectMapper mapper) {
    super(ExecutionStore.ORCHESTRATION, Orchestration, jedis, mapper)
  }

  @Override
  List<Orchestration> allForApplication(String application) {
    def appKey = getAppKey(application)
    if (jedis.exists(appKey)) {
      def len = jedis.llen(getAppKey(appKey))
      def pipelineJsons = jedis.lrange(appKey, 0, len)
      mapper.readValue(pipelineJsons, new TypeReference<List<Orchestration>>() {})
    } else {
      []
    }
  }
}
