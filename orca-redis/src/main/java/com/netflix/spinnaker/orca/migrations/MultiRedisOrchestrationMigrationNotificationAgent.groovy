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
import com.netflix.spinnaker.orca.front50.model.Application
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
import rx.schedulers.Schedulers

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Slf4j
@Component
@ConditionalOnExpression(value = '${pollers.multiRedisOrchestrationMigration.enabled:false}')
@ConditionalOnBean(name = "previousRedisClientDelegate")
class MultiRedisOrchestrationMigrationNotificationAgent extends AbstractPollingNotificationAgent {
  final String notificationType = "multiRedisOrchestrationMigration"

  RedisClientDelegate redisClientDelegate
  RedisExecutionRepository executionRepository
  RedisExecutionRepository executionRepositoryPrevious
  RedisClientDelegate previousRedisClientDelegate

  @Autowired(required = false)
  Front50Service front50Service

  @Value('${pollers.multiRedisOrchestrationMigration.intervalMs:3600000}')
  long pollingIntervalMs

  @Autowired
  MultiRedisOrchestrationMigrationNotificationAgent(@Qualifier("jedisPool") Pool<Jedis> jedisPool,
                                                    @Qualifier("redisClientDelegate") RedisClientDelegate redisClientDelegate,
                                                    @Qualifier("previousRedisClientDelegate") RedisClientDelegate previousRedisClientDelegate
  ) {
    super(jedisPool)
    this.redisClientDelegate = redisClientDelegate
    this.previousRedisClientDelegate = previousRedisClientDelegate

    def queryAllScheduler = Schedulers.from(Executors.newFixedThreadPool(1))
    def queryByAppScheduler = Schedulers.from(Executors.newFixedThreadPool(1))

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
      .filter({ interval -> tryAcquireLock() })
      .subscribe({ interval ->
      try {
        migrate()
      } catch (Exception e) {
        log.error("Migration error", e)
      }
    })
  }

  void migrate() {
    if (!front50Service) {
      throw new UnsupportedOperationException("Front50 is not enabled, fix this by setting front50.enabled: true")
    }
    log.info("Starting Orchestration Migration...")

    def previouslyMigratedOrchestrationIds = new HashSet<String>()
    redisClientDelegate.withCommandsClient(new Consumer<JedisCommands>() {
      @Override
      void accept(JedisCommands jedisCommands) {
        previouslyMigratedOrchestrationIds.addAll(jedisCommands.smembers("allJobs:orchestration"))
      }
    })

    def executionCriteria = new ExecutionRepository.ExecutionCriteria(limit: 1000)
    executionCriteria.statuses = ExecutionStatus.values().findAll { it.complete }.collect { it.name() }

    def allApplications = front50Service.getAllApplications()
    log.info("Found ${allApplications.size()} applications")

    allApplications.eachWithIndex { Application application, int index ->
      def applicationName = application.name.toLowerCase()
      def unmigratedOrchestrations = executionRepositoryPrevious
        .retrieveOrchestrationsForApplication(applicationName, executionCriteria)
        .filter({ orchestration -> !previouslyMigratedOrchestrationIds.contains(orchestration.id) })
        .toList()
        .toBlocking()
        .single()

      def migratableOrchestrations = unmigratedOrchestrations.findAll { it.status.isComplete() }
      def pendingOrchestrations = unmigratedOrchestrations.findAll { !it.status.isComplete() }

      if (!pendingOrchestrations.isEmpty()) {
        log.info("${pendingOrchestrations.size()} orchestrations yet to complete ${applicationName}) [${index}/${allApplications.size()}]")
      }

      if (migratableOrchestrations.isEmpty()) {
        return
      }

      log.info("${migratableOrchestrations.size()} orchestrations to migrate (${applicationName}) [${index}/${allApplications.size()}]")

      migratableOrchestrations.each {
        def execution = executionRepositoryPrevious.retrieve(it.type, it.id)
        executionRepository.store(execution)
      }

      log.info("${migratableOrchestrations.size()} orchestrations migrated (${applicationName}) [${index}/${allApplications.size()}]")
    }
  }
}
