/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.sql.cleanup

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.config.OrcaSqlProperties
import com.netflix.spinnaker.config.TopApplicationExecutionCleanupAgentConfigurationProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.time.Instant

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.*
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import static java.time.temporal.ChronoUnit.DAYS

abstract class TopApplicationExecutionCleanupPollingNotificationAgentSpec extends Specification {
  @Shared
  ObjectMapper mapper = OrcaObjectMapper.newInstance().with {
    registerModule(new KotlinModule.Builder().build())
    it
  }

  abstract SqlTestUtil.TestDatabase getDatabase()

  @Shared
  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase

  @Shared
  ExecutionRepository executionRepository

  static final regularApp = "app"
  static final exceptionApp1 = "exceptionApp1"
  static final exceptionApp2 = "exceptionApp2"

  def cleanupAgent = new TopApplicationExecutionCleanupPollingNotificationAgent(
    Mock(NotificationClusterLock),
    currentDatabase.context,
    new NoopRegistry(),
    executionRepository,
    new TopApplicationExecutionCleanupAgentConfigurationProperties(
        0L,
        2,
        1,
        [
            exceptionApp1 : 4,
            exceptionApp2 : 5
        ]
    ),
    new OrcaSqlProperties(),
    []
  )

  def setupSpec() {
    currentDatabase = getDatabase()
    executionRepository = new SqlExecutionRepository("test", currentDatabase.context, mapper, new RetryProperties(), 10, 100, "poolName", "readPoolName", null, [], new ExecutionCompressionProperties(), false, Mock(DataSource))
  }

  def cleanup() {
    cleanupDb(currentDatabase.context)
  }

  def "should preserve threshold executions"() {
    given:
    def criteria = new ExecutionRepository.ExecutionCriteria(statuses: ["SUCCEEDED"])
    (1..10).each {
      executionRepository.store(buildExecution(regularApp, 10 + it))
    }

    when:
    def allExecutions = executionRepository.retrieveOrchestrationsForApplication(regularApp, criteria, null)

    then:
    allExecutions.size() == 10

    when: 'agent runs'
    cleanupAgent.tick()
    allExecutions = executionRepository.retrieveOrchestrationsForApplication(regularApp, criteria, null)

    then: 'preserves threshold (2) orchestrations'
    allExecutions*.name.sort() == ["#11", "#12"]
  }

  def "should preserve threshold executions for exceptional applications"() {
    given:
    def criteria = new ExecutionRepository.ExecutionCriteria(statuses: ["SUCCEEDED"])
    (1..10).each {
      executionRepository.store(buildExecution(exceptionApp1, 10 + it))
      executionRepository.store(buildExecution(exceptionApp2, 10 + it))
    }

    when:
    def allExecutionsApp1 = executionRepository.retrieveOrchestrationsForApplication(exceptionApp1, criteria, null)
    def allExecutionsApp2 = executionRepository.retrieveOrchestrationsForApplication(exceptionApp2, criteria, null)

    then:
    allExecutionsApp1.size() == 10
    allExecutionsApp2.size() == 10

    when: 'agent runs'
    cleanupAgent.tick()
    allExecutionsApp1 = executionRepository.retrieveOrchestrationsForApplication(exceptionApp1, criteria, null)
    allExecutionsApp2 = executionRepository.retrieveOrchestrationsForApplication(exceptionApp2, criteria, null)

    then: 'preserves correct number of executions for given application'
    allExecutionsApp1*.name.sort() == ["#11", "#12", "#13", "#14"]
    allExecutionsApp2*.name.sort() == ["#11", "#12", "#13", "#14", "#15"]
  }

  PipelineExecutionImpl buildExecution(String application, int daysOffset) {
    PipelineExecutionImpl e = new PipelineExecutionImpl(ORCHESTRATION, application)
    e.status = ExecutionStatus.SUCCEEDED
    e.pipelineConfigId = "orchestration-001"
    e.buildTime = Instant.now().minus(daysOffset, DAYS).toEpochMilli()

    e.name = "#${daysOffset.toString().padLeft(2, "0")}"
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [waitTime: 10]))

    return e
  }
}

class MySqlTopApplicationExecutionCleanupPollingNotificationAgentSpec extends TopApplicationExecutionCleanupPollingNotificationAgentSpec {
  @Override
  SqlTestUtil.TestDatabase getDatabase() {
    return initTcMysqlDatabase()
  }
}

class PgTopApplicationExecutionCleanupPollingNotificationAgentSpec extends TopApplicationExecutionCleanupPollingNotificationAgentSpec {
  @Override
  SqlTestUtil.TestDatabase getDatabase() {
    return initTcPostgresDatabase()
  }
}
