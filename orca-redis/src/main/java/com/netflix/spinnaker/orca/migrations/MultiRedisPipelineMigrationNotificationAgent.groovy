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

package com.netflix.spinnaker.orca.migrations

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.RedisExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.util.Pool
import rx.Observable
import rx.Scheduler

import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Slf4j
@Component
@ConditionalOnExpression(value = '${pollers.multiRedisPipelineMigration.enabled:false}')
@ConditionalOnBean(name="previousRedisClientDelegate")
class MultiRedisPipelineMigrationNotificationAgent extends AbstractPollingNotificationAgent {
  final String notificationType = "multiRedisOrchestrationMigration"

  Front50Service front50Service

  RedisClientDelegate redisClientDelegate
  RedisExecutionRepository executionRepository
  RedisExecutionRepository executionRepositoryPrevious
  RedisClientDelegate previousRedisClientDelegate

  @Value('${pollers.multiRedisPipelineMigration.intervalMs:3600000}')
  long pollingIntervalMs

  @Autowired
  MultiRedisPipelineMigrationNotificationAgent(
    @Qualifier("jedisPool") Pool<Jedis> jedisPool,
    @Qualifier("redisClientDelegate") RedisClientDelegate redisClientDelegate,
    @Qualifier("previousRedisClientDelegate") RedisClientDelegate previousRedisClientDelegate,
    @Qualifier("queryAllScheduler") Scheduler queryAllScheduler,
    @Qualifier("queryByAppScheduler") Scheduler queryByAppScheduler,
    Front50Service front50Service
  ) {
    super(jedisPool)
    this.redisClientDelegate = redisClientDelegate
    this.previousRedisClientDelegate = previousRedisClientDelegate
    this.front50Service = front50Service

    this.executionRepository = new RedisExecutionRepository(
      new RedisClientSelector([redisClientDelegate]), queryAllScheduler, queryByAppScheduler, 75
    )
    this.executionRepositoryPrevious = new RedisExecutionRepository(
      new RedisClientSelector([previousRedisClientDelegate]), queryAllScheduler, queryByAppScheduler, 75
    )
  }

  @Override
  long getPollingInterval() {
    return pollingIntervalMs / 1000
  }

  @Override
  void startPolling() {
    subscription = Observable
      .timer(pollingInterval, TimeUnit.SECONDS, scheduler)
      .repeat()
      .filter({interval -> tryAcquireLock()})
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
    redisClientDelegate.withCommandsClient(new Consumer<JedisCommands>() {
      @Override
      void accept(JedisCommands jedisCommands) {
        previouslyMigratedPipelineIds.addAll(jedisCommands.smembers("allJobs:pipeline"))
      }
    })

    def executionCriteria = new ExecutionRepository.ExecutionCriteria(limit: 50)
    executionCriteria.statuses = ExecutionStatus.values().findAll {
      it.complete
    }.collect { it.name() }

    def allPipelineConfigIds = front50Service.allPipelines*.id + front50Service.allStrategies*.id
    log.info("Found ${allPipelineConfigIds.size()} pipeline configs")

    allPipelineConfigIds.eachWithIndex { String pipelineConfigId, int index ->
      def unmigratedPipelines = executionRepositoryPrevious
        .retrievePipelinesForPipelineConfigId(pipelineConfigId, executionCriteria)
        .filter({ pipeline -> !previouslyMigratedPipelineIds.contains(pipeline.id) })
        .toList()
        .toBlocking()
        .single()

      def migratablePipelines = unmigratedPipelines.findAll { it.status.isComplete() }
      def pendingPipelines = unmigratedPipelines.findAll { !it.status.isComplete() }

      if (!pendingPipelines.isEmpty()) {
        log.info("${pendingPipelines.size()} pipelines yet to complete (${pipelineConfigId}) [${index}/${allPipelineConfigIds.size()}]")
      }

      if (migratablePipelines.isEmpty()) {
        return
      }

      log.info("${migratablePipelines.size()} pipelines to migrate (${pipelineConfigId}) [${index}/${allPipelineConfigIds.size()}]")

      migratablePipelines.each {
        def execution = executionRepositoryPrevious.retrieve(it.type, it.id)
        executionRepository.store(execution)
      }

      log.info("${migratablePipelines.size()} pipelines migrated (${pipelineConfigId}) [${index}/${allPipelineConfigIds.size()}]")
    }
  }
}
