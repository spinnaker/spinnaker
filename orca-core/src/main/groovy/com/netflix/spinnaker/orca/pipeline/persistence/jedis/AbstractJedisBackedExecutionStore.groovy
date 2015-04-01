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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.*
import redis.clients.jedis.JedisCommands

abstract class AbstractJedisBackedExecutionStore<T extends Execution> implements ExecutionStore<T> {
  private final String prefix
  private final Class<T> executionClass
  protected final JedisCommands jedis
  protected final ObjectMapper mapper

  AbstractJedisBackedExecutionStore(String prefix, Class<T> executionClass, JedisCommands jedis, ObjectMapper mapper) {
    this.prefix = prefix
    this.executionClass = executionClass
    this.jedis = jedis
    this.mapper = mapper
  }

  protected String getAlljobsKey() {
    "allJobs:${prefix}"
  }

  protected String getAppKey(String app) {
    "${prefix}:app:${app}"
  }

  @Override
  List<T> all() {
    retrieveList(alljobsKey)
  }

  @Override
  List<T> allForApplication(String application) {
    retrieveList(getAppKey(application))
  }

  @Override
  void store(T execution) {
    if (!execution.id) {
      execution.id = UUID.randomUUID().toString()
      jedis.sadd(alljobsKey, execution.id)
      def appKey = getAppKey(execution.application)
      jedis.sadd(appKey, execution.id)
    }
    def json = mapper.writeValueAsString(execution)

    def key = "${prefix}:$execution.id"
    jedis.hset(key, "config", json)
  }

  @Override
  void storeStage(Stage<T> stage) {
    def json = mapper.writeValueAsString(stage)

    def key = "${prefix}:stage:${stage.id}"
    jedis.hset(key, "config", json)
  }

  @Override
  void delete(String id) {
    def key = "${prefix}:$id"
    def storePrefix = prefix
    try {
      T item = retrieve(id)
      jedis.hdel(key, "config")
      jedis.srem(alljobsKey, id)
      def appKey = getAppKey(item.application)
      jedis.srem(appKey, id)
      item.stages.each { Stage stage ->
        def stageKey = "${storePrefix}:stage:${stage.id}"
        jedis.hdel(stageKey, "config")
      }
    } catch (ExecutionNotFoundException ignored) { }
  }

  @Override
  Stage<T> retrieveStage(String id) {
    def key = "${prefix}:stage:${id}"
    return jedis.exists(key) ? mapper.readValue(jedis.hget(key, "config"), Stage) : null
  }

  @Override
  T retrieve(String id) throws ExecutionNotFoundException {
    def key = "${prefix}:$id"
    if (jedis.exists(key)) {
      def json = jedis.hget(key, "config")
      Execution execution = mapper.readValue(json, executionClass)

      List<Stage> reorderedStages = []
      execution.stages.findAll { it.parentStageId == null }.each { Stage<T> parentStage ->
        reorderedStages << parentStage

        def children = new LinkedList<Stage<T>>(execution.stages.findAll { it.parentStageId == parentStage.id })
        while (!children.isEmpty()) {
          def child = children.remove(0)
          children.addAll(0, execution.stages.findAll { it.parentStageId == child.id})
          reorderedStages << child
        }
      }
      execution.stages = reorderedStages.collect {
        def explicitStage = retrieveStage(it.id) ?: it
        explicitStage.execution = execution
        return explicitStage
      }
      execution
    } else {
      throw new ExecutionNotFoundException("No ${prefix} execution found for $id")
    }
  }

  private List<T> retrieveList(String lookupKey) {
    def results = []
    if (jedis.exists(lookupKey)) {
      def keys = jedis.smembers(lookupKey)
      for (id in keys) {
        try {
          results << retrieve(id)
        } catch (ExecutionNotFoundException ignored) {}
      }
    }
    results
  }
}
