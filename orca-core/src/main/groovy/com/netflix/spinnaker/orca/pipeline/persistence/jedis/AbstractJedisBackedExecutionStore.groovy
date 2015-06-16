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
import com.netflix.spectator.api.ValueFunction
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.*
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import redis.clients.jedis.JedisCommands
import rx.Observable
import rx.Scheduler
import rx.Subscriber
import rx.schedulers.Schedulers

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

@Slf4j
abstract class AbstractJedisBackedExecutionStore<T extends Execution> implements ExecutionStore<T> {
  private final Executor fetchAllExecutor = Executors.newFixedThreadPool(10)
  private final Executor fetchApplicationExecutor
  private final int chunkSize

  private final String prefix
  private final Class<T> executionClass
  protected final JedisCommands jedis
  protected final ObjectMapper mapper

  AbstractJedisBackedExecutionStore(String prefix,
                                    Class<T> executionClass,
                                    JedisCommands jedis,
                                    ObjectMapper mapper,
                                    int threadPoolSize,
                                    int threadPoolChunkSize,
                                    ExtendedRegistry extendedRegistry) {
    this.prefix = prefix
    this.executionClass = executionClass
    this.jedis = jedis
    this.mapper = mapper
    this.fetchApplicationExecutor = Executors.newFixedThreadPool(threadPoolSize)
    this.chunkSize = threadPoolChunkSize

    def createGuage = { Executor executor, String threadPoolName, String valueName ->
      def id = extendedRegistry
        .createId("threadpool.${valueName}" as String)
        .withTag("id", threadPoolName)

      extendedRegistry.gauge(id, executor, new ValueFunction() {
        @Override
        double apply(Object ref) {
          ((ThreadPoolExecutor)ref)."${valueName}"
        }
      })
    }

    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "activeCount")
    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "maximumPoolSize")
    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "corePoolSize")
    createGuage.call(fetchAllExecutor, "${getClass().simpleName}-fetchAll", "poolSize")

    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "activeCount")
    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "maximumPoolSize")
    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "corePoolSize")
    createGuage.call(fetchApplicationExecutor, "${getClass().simpleName}-fetchApplication", "poolSize")
  }

  protected String getAlljobsKey() {
    "allJobs:${prefix}"
  }

  protected String getAppKey(String app) {
    "${prefix}:app:${app}"
  }

  @Override
  rx.Observable<T> all() {
    retrieveObservable(alljobsKey, Schedulers.from(fetchAllExecutor), chunkSize)
  }

  @Override
  rx.Observable<T> allForApplication(String application) {
    retrieveObservable(getAppKey(application), Schedulers.from(fetchApplicationExecutor), chunkSize)
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
      def appKey = getAppKey(item.application)
      jedis.srem(appKey, id)

      item.stages.each { Stage stage ->
        def stageKey = "${storePrefix}:stage:${stage.id}"
        jedis.hdel(stageKey, "config")
      }
    } catch (ExecutionNotFoundException ignored) {
      // do nothing
    } finally {
      jedis.hdel(key, "config")
      jedis.srem(alljobsKey, id)
    }
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
          children.addAll(0, execution.stages.findAll { it.parentStageId == child.id })
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

  @CompileDynamic
  private Observable<Execution> retrieveObservable(String lookupKey, Scheduler scheduler, int chunkSize) {
    Observable
      .just(lookupKey)
      .flatMapIterable { String key -> jedis.smembers(lookupKey) }
      .buffer(chunkSize)
      .flatMap { Collection<String> ids ->
      Observable
        .from(ids)
        .flatMap { String executionId ->
        try {
          return Observable.just(retrieve(executionId))
        } catch (ExecutionNotFoundException ignored) {
          log.info("Execution (${executionId}) does not exist")
          delete(executionId)
          jedis.srem(lookupKey, executionId)
        }
        return Observable.empty()
      }
      .subscribeOn(scheduler)
    }
  }
}
