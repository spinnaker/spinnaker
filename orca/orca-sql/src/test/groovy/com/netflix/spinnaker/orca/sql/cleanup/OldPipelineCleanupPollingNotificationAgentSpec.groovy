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

import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.config.OldPipelineCleanupAgentConfigurationProperties
import com.netflix.spinnaker.config.OrcaSqlProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil

import javax.sql.DataSource
import java.time.Clock
import java.time.Instant
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.NoopRegistry
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

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initTcPostgresDatabase
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initTcMysqlDatabase
import static java.time.temporal.ChronoUnit.DAYS

abstract class OldPipelineCleanupPollingNotificationAgentSpec extends Specification {
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

  def cleanupAgent = new OldPipelineCleanupPollingNotificationAgent(
    Mock(NotificationClusterLock),
    currentDatabase.context,
    Clock.systemDefaultZone(),
    new NoopRegistry(),
    executionRepository,
    new OldPipelineCleanupAgentConfigurationProperties(
        0L,
        10L, // threshold days
        5,  // minimum pipeline executions
        1,
        ["exceptionalApp"],
        50
    ),
    new OrcaSqlProperties(),
    []
  )

  def setupSpec() {
    currentDatabase = getDatabase()
    executionRepository = new SqlExecutionRepository("test",
        currentDatabase.context,
        mapper,
        new RetryProperties(),
        10,
        100,
        "poolName",
        "readPoolName",
        null,
        [],
        new ExecutionCompressionProperties(),
        false,
        Mock(DataSource))
  }

  def cleanup() {
    cleanupDb(currentDatabase.context)
  }

  def "should preserve the most recent 5 executions when cleaning up old pipeline executions"() {
    given:
    def app = "app"
    (1..10).each {
      executionRepository.store(buildExecution(app, 10 + it))
    }
    executionRepository.store(buildExecution(app, 1))
    executionRepository.store(buildExecution(app, 2))

    when:
    def allExecutions = executionRepository.retrievePipelinesForApplication(app).toList().blockingGet().unique()

    then:
    allExecutions.size() == 12

    when:
    cleanupAgent.tick()
    allExecutions = executionRepository.retrievePipelinesForApplication(app).toList().blockingGet().unique()

    then:
    // preserve any execution more recent than `thresholdDays` _AND_
    // the most recent `minimumPipelineExecutions` older than `thresholdDays`
    allExecutions*.name.sort() == ["#01", "#02", "#11", "#12", "#13", "#14", "#15"]
  }

  def "should handle exceptional applications"() {
    given:
    def app = "exceptionalApp"
    (1..10).each {
      executionRepository.store(buildExecution(app, 50 + it))
    }
    executionRepository.store(buildExecution(app, 9))
    executionRepository.store(buildExecution(app, 49))

    when:
    def allExecutions = executionRepository.retrievePipelinesForApplication(app).toList().blockingGet().unique()

    then:
    allExecutions.size() == 12

    when:
    cleanupAgent.tick()
    allExecutions = executionRepository.retrievePipelinesForApplication(app).toList().blockingGet().unique()

    then:
    // preserve any execution more recent than `thresholdDays` _AND_
    // the most recent `minimumPipelineExecutions` older than `thresholdDays`
    allExecutions*.name.sort() == ["#09", "#49", "#51", "#52", "#53", "#54", "#55"]
  }

  PipelineExecutionImpl buildExecution(String application, int daysOffset) {
    PipelineExecutionImpl e = new PipelineExecutionImpl(PIPELINE, application)
    e.status = ExecutionStatus.SUCCEEDED
    e.pipelineConfigId = "pipeline-001"
    e.buildTime = Instant.now().minus(daysOffset, DAYS).toEpochMilli()

    e.name = "#${daysOffset.toString().padLeft(2, "0")}"
    e.stages.add(new StageExecutionImpl(e, "wait", "wait stage", [waitTime: 10]))

    return e
  }
}

class MySqlOldPipelineCleanupPollingNotificationAgentSpec extends OldPipelineCleanupPollingNotificationAgentSpec {
  @Override
  SqlTestUtil.TestDatabase getDatabase() {
    return initTcMysqlDatabase()
  }
}

class PgOldPipelineCleanupPollingNotificationAgentSpec extends OldPipelineCleanupPollingNotificationAgentSpec {
  @Override
  SqlTestUtil.TestDatabase getDatabase() {
    return initTcPostgresDatabase()
  }
}
