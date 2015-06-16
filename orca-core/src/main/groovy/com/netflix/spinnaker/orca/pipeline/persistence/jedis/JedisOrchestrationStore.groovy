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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import groovy.util.logging.Slf4j
import redis.clients.jedis.JedisCommands

@Slf4j
class JedisOrchestrationStore extends AbstractJedisBackedExecutionStore<Orchestration> {
  JedisOrchestrationStore(JedisCommands jedis, ObjectMapper mapper, int threadPoolSize, int threadPoolChunkSize, ExtendedRegistry extendedRegistry) {
    super(ExecutionStore.ORCHESTRATION, Orchestration, jedis, mapper, threadPoolSize, threadPoolChunkSize, extendedRegistry)
  }
}
