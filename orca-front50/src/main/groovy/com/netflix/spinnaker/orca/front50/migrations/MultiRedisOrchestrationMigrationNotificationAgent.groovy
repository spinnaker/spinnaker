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
import com.netflix.spinnaker.orca.front50.model.Application
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
@ConditionalOnExpression(value = '${pollers.multiRedisOrchestrationMigration.enabled:false}')
class MultiRedisOrchestrationMigrationNotificationAgent extends AbstractPollingNotificationAgent {
  final String notificationType = "multiRedisOrchestrationMigration"

  Pool<Jedis> jedisPool
  Pool<Jedis> jedisPoolPrevious
  JedisExecutionRepository executionRepositoryPrevious

  @Autowired(required = false)
  Front50Service front50Service

  @Value('${pollers.multiRedisOrchestrationMigration.intervalMs:3600000}')
  long pollingIntervalMs

  @Autowired
  MultiRedisOrchestrationMigrationNotificationAgent(ObjectMapper objectMapper,
                                                    Client jesqueClient,
                                                    Registry registry,
                                                    @Qualifier("jedisPool") Pool<Jedis> jedisPool,
                                                    @Qualifier("jedisPoolPrevious") Pool<Jedis> jedisPoolPrevious) {
    super(objectMapper, jesqueClient)
    this.jedisPool = jedisPool
    this.jedisPoolPrevious = jedisPoolPrevious

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
    if (!front50Service) {
      throw new UnsupportedOperationException("Front50 is not enabled, fix this by setting front50.enabled: true")
    }
    log.info("Starting Orchestration Migration...")

    def previouslyMigratedOrchestrationIds = new HashSet<String>()
    withJedis(jedisPool) { jedis ->
      previouslyMigratedOrchestrationIds.addAll(jedis.smembers("allJobs:orchestration"))
    }

    def executionCriteria = new ExecutionRepository.ExecutionCriteria(limit: 1000)
    executionCriteria.statuses = ExecutionStatus.values().findAll { it.complete }.collect { it.name() }

    def allApplications = front50Service.getAllApplications()
    log.info("Found ${allApplications.size()} applications")

    allApplications.eachWithIndex { Application application, int index ->
      def applicationName = application.name.toLowerCase()
      def migratableOrchestrations = executionRepositoryPrevious
        .retrieveOrchestrationsForApplication(applicationName, executionCriteria)
        .filter({ orchestration -> orchestration.status.isComplete() && !previouslyMigratedOrchestrationIds.contains(orchestration.id) })
        .toList()
        .toBlocking()
        .single()

      if (migratableOrchestrations.isEmpty()) {
        return
      }

      log.info("${migratableOrchestrations.size()} orchestrations to migrate (${applicationName}) [${index}/${allApplications.size()}]")

      List<byte[]> sourceDumps
      withJedis(jedisPoolPrevious) { jedis ->
        def sourcePipe = jedis.pipelined()

        migratableOrchestrations.each {
          sourcePipe.dump("orchestration:${it.id}")
        }

        sourceDumps = sourcePipe.syncAndReturnAll()
      }

      withJedis(jedisPool) { jedis ->
        def destPipe = jedis.pipelined()
        sourceDumps.eachWithIndex { byte[] entry, int i ->
          def orchestration = migratableOrchestrations[i]
          destPipe.restore("orchestration:${orchestration.id}", 0, entry)
          destPipe.sadd("allJobs:orchestration", orchestration.id)
          destPipe.sadd("orchestration:app:${orchestration.application.toLowerCase()}", orchestration.id)
        }
        destPipe.sync()
      }

      log.info("${migratableOrchestrations.size()} orchestrations migrated (${applicationName}) [${index}/${allApplications.size()}]")
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
