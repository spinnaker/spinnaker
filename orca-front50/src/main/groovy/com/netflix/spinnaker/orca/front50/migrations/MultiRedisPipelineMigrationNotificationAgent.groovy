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


package com.netflix.spinnaker.orca.front50.migrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import groovy.util.logging.Slf4j
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import rx.Observable
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit
import java.util.function.Function

@Slf4j
@Component
@ConditionalOnExpression(value = '${pollers.multiRedisPipelineMigration.enabled:false}')
class MultiRedisPipelineMigrationNotificationAgent extends AbstractPollingNotificationAgent {
  final String notificationType = "multiRedisOrchestrationMigration"

  Pool<Jedis> jedisPool
  Pool<Jedis> jedisPoolPrevious
  Front50Service front50Service
  JedisExecutionRepository executionRepositoryPrevious

  @Value('${pollers.multiRedisPipelineMigration.intervalMs:3600000}')
  long pollingIntervalMs

  @Autowired
  MultiRedisPipelineMigrationNotificationAgent(ObjectMapper objectMapper,
                                               Client jesqueClient,
                                               Registry registry,
                                               @Qualifier("jedisPool") Pool<Jedis> jedisPool,
                                               @Qualifier("jedisPoolPrevious") Pool<Jedis> jedisPoolPrevious,
                                               Front50Service front50Service) {
    super(objectMapper, jesqueClient)
    this.jedisPool = jedisPool
    this.jedisPoolPrevious = jedisPoolPrevious
    this.front50Service = front50Service

    def queryAllScheduler = Schedulers.from(JedisExecutionRepository.newFixedThreadPool(registry, 1, "QueryAll"))
    def queryByAppScheduler = Schedulers.from(JedisExecutionRepository.newFixedThreadPool(registry, 1, "QueryByApp"))
    this.executionRepositoryPrevious = new JedisExecutionRepository(jedisPoolPrevious, Optional.empty(), queryAllScheduler, queryByAppScheduler, 75)
  }

  @Override
  long getPollingInterval() {
    return pollingIntervalMs / 1000
  }

  void startPolling() {
    subscription = Observable
      .timer(pollingInterval, TimeUnit.SECONDS, scheduler)
      .repeat()
      .subscribe({ interval ->
      try {
        migrate()
      } catch (Exception e) {
        log.error("Migration error", e)
      }
    })
  }

  void migrate() {
    log.info("Starting Pipeline Migration...")

    def previouslyMigratedPipelineIds = new HashSet<String>()
    withJedis(jedisPool) { jedis ->
      previouslyMigratedPipelineIds.addAll(jedis.smembers("allJobs:pipeline"))
    }

    def executionCriteria = new ExecutionRepository.ExecutionCriteria(limit: 50)
    executionCriteria.statuses = ExecutionStatus.values().findAll { it.complete }.collect { it.name() }

    def allPipelineConfigIds = front50Service.allPipelines*.id + front50Service.allStrategies*.id
    log.info("Found ${allPipelineConfigIds.size()} pipeline configs")

    allPipelineConfigIds.eachWithIndex { String pipelineConfigId, int index ->
      def migratablePipelines = executionRepositoryPrevious
        .retrievePipelinesForPipelineConfigId(pipelineConfigId, executionCriteria)
        .filter({ pipeline -> pipeline.status.isComplete() && !previouslyMigratedPipelineIds.contains(pipeline.id) })
        .toList()
        .toBlocking()
        .single()

      if (migratablePipelines.isEmpty()) {
        return
      }

      log.info("${migratablePipelines.size()} pipelines to migrate (${pipelineConfigId}) [${index}/${allPipelineConfigIds.size()}]")

      List<byte[]> sourceDumps
      withJedis(jedisPoolPrevious) { jedis ->
        def sourcePipe = jedis.pipelined()

        migratablePipelines.each {
          sourcePipe.dump("pipeline:${it.id}")
        }

        sourceDumps = sourcePipe.syncAndReturnAll()
      }

      withJedis(jedisPool) { jedis ->
        def destPipe = jedis.pipelined()
        sourceDumps.eachWithIndex { byte[] entry, int i ->
          def pipeline = migratablePipelines[i]
          destPipe.restore("pipeline:${pipeline.id}", 0, entry)
          destPipe.sadd("allJobs:pipeline", pipeline.id)
          destPipe.sadd("pipeline:app:${pipeline.application.toLowerCase()}", pipeline.id)
          destPipe.zadd(JedisExecutionRepository.executionsByPipelineKey(pipeline.pipelineConfigId), pipeline.buildTime, pipeline.id)
        }
        destPipe.sync()
      }

      log.info("${migratablePipelines.size()} pipelines migrated (${pipelineConfigId}) [${index}/${allPipelineConfigIds.size()}]")
    }


  }

  private <T> T withJedis(Pool<Jedis> jedisPool, Function<Jedis, T> action) {
    jedisPool.resource.withCloseable(action.&apply)
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    throw new UnsupportedOperationException()
  }

  @Override
  protected Observable<Execution> getEvents() {
    throw new UnsupportedOperationException()
  }
}
