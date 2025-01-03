/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.config.TaskControllerConfigurationProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import com.nhaarman.mockito_kotlin.mock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TaskControllerTest : JUnit5Minutests {
  data class Fixture(val optimizeExecution: Boolean) {

    private val clock: Clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
    val database: SqlTestUtil.TestDatabase = SqlTestUtil.initTcMysqlDatabase()!!

    private val executionRepository: SqlExecutionRepository = SqlExecutionRepository(
      partitionName = "test",
      jooq = database.context,
      mapper = OrcaObjectMapper.getInstance(),
      retryProperties = RetryProperties(),
      compressionProperties = ExecutionCompressionProperties(),
      pipelineRefEnabled = false,
      dataSource = mock()
    )

    private val taskControllerConfigurationProperties: TaskControllerConfigurationProperties = TaskControllerConfigurationProperties()
      .apply {
        optimizeExecutionRetrieval = optimizeExecution
      }

    private val daysOfExecutionHistory: Long = taskControllerConfigurationProperties.daysOfExecutionHistory.toLong()

    private val front50Service: Front50Service = mock()

    private val taskController: TaskController = TaskController(
      front50Service,
      executionRepository,
      mock(),
      mock(),
      listOf(mock()),
      ContextParameterProcessor(),
      mock(),
      OrcaObjectMapper.getInstance(),
      NoopRegistry(),
      mock(),
      taskControllerConfigurationProperties
    )

    val subject: MockMvc = MockMvcBuilders.standaloneSetup(taskController).build()

    fun setup() {
      database.context
        .insertInto(table("pipelines"),
          listOf(
            field("config_id"),
            field("id"),
            field("application"),
            field("build_time"),
            field("start_time"),
            field("body"),
            field("status")
          ))
        .values(
          listOf(
            "1",
            "1-exec-id-1",
            "test-app",
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(120, ChronoUnit.MINUTES).toEpochMilli(),
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(120, ChronoUnit.HOURS).toEpochMilli(),
            "{\"id\": \"1-exec-id-1\", \"type\": \"PIPELINE\", \"pipelineConfigId\": \"1\"}",
            "SUCCEEDED"
          )
        )
        .values(
          listOf(
            "1",
            "1-exec-id-2",
            "test-app",
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(115, ChronoUnit.MINUTES).toEpochMilli(),
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(115, ChronoUnit.MINUTES).toEpochMilli(),
            "{\"id\": \"1-exec-id-2\", \"type\": \"PIPELINE\", \"pipelineConfigId\": \"1\"}",
            "TERMINAL"
          )
        )
        .values(
          listOf(
            "1",
            "1-exec-id-3",
            "test-app",
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(114, ChronoUnit.MINUTES).toEpochMilli(),
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(114, ChronoUnit.MINUTES).toEpochMilli(),
            "{\"id\": \"1-exec-id-3\", \"type\": \"PIPELINE\", \"pipelineConfigId\": \"1\"}",
            "RUNNING"
          )
        )
        .values(
          listOf(
            "2",
            "2-exec-id-1",
            "test-app",
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS).toEpochMilli(),
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS).toEpochMilli(),
            "{\"id\": \"2-exec-id-1\", \"type\": \"PIPELINE\", \"pipelineConfigId\": \"2\"}",
            "NOT_STARTED"
          )
        )
        .values(
          listOf(
            "3",
            "3-exec-id-1",
            "test-app-2",
            clock.instant().minus(daysOfExecutionHistory + 1, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS).toEpochMilli(),
            clock.instant().minus(daysOfExecutionHistory + 1, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS).toEpochMilli(),
            "{\"id\": \"3-exec-id-1\", \"type\": \"PIPELINE\", \"pipelineConfigId\": \"3\"}",
            "STOPPED"
          )
        )
        .execute()
      Mockito.`when`(front50Service.getPipelines("test-app", false))
        .thenReturn(
          listOf(
            mapOf("id" to "1"),
            mapOf("id" to "2"))
        )

      Mockito.`when`(front50Service.getStrategies("test-app"))
        .thenReturn(listOf())
    }

    fun cleanUp() {
      SqlTestUtil.cleanupDb(database.context)
    }
  }

  fun tests() = rootContext<Fixture> {
    context("execution retrieval without optimization") {
      fixture {
        Fixture(false)
      }

      before { setup() }
      after { cleanUp() }

      test("retrieve executions with limit = 2 & expand = false") {
        expectThat(database.context.fetchCount(table("pipelines"))).isEqualTo(5)
        val response = subject.perform(get("/applications/test-app/pipelines?limit=2&expand=false")).andReturn().response
        val results = OrcaObjectMapper.getInstance().readValue(response.contentAsString, object : TypeReference<List<PipelineExecution>>() {})
        val expectedOutput = listOf("1-exec-id-2", "1-exec-id-3","2-exec-id-1")
        expectThat(results.size).isEqualTo(3)
        results.forEach {
          assert(it.id in expectedOutput)
        }
      }

      test("retrieve executions with limit = 2 & expand = false with statuses") {
        expectThat(database.context.fetchCount(table("pipelines"))).isEqualTo(5)
        val response = subject.perform(get(
          "/applications/test-app/pipelines?limit=2&expand=false&statuses=RUNNING,SUSPENDED,PAUSED,NOT_STARTED")
        ).andReturn().response
        val results = OrcaObjectMapper.getInstance().readValue(response.contentAsString, object : TypeReference<List<PipelineExecution>>() {})
        val expectedOutput = listOf("1-exec-id-3","2-exec-id-1")
        expectThat(results.size).isEqualTo(2)
        results.forEach {
          assert(it.id in expectedOutput)
        }
      }
    }

    context("execution retrieval with optimization") {
      fixture {
        Fixture(true)
      }

      before { setup() }
      after { cleanUp() }

      test("retrieve executions with limit = 2 & expand = false") {
        expectThat(database.context.fetchCount(table("pipelines"))).isEqualTo(5)
        val response = subject.perform(get("/applications/test-app/pipelines?limit=2&expand=false")).andReturn().response
        val results = OrcaObjectMapper.getInstance().readValue(response.contentAsString, object : TypeReference<List<PipelineExecution>>() {})
        val expectedOutput = listOf("1-exec-id-2", "1-exec-id-3","2-exec-id-1")
        expectThat(results.size).isEqualTo(3)
        results.forEach {
          assert(it.id in expectedOutput)
        }
      }

      test("retrieve executions with limit = 2 & expand = false with statuses") {
        expectThat(database.context.fetchCount(table("pipelines"))).isEqualTo(5)
        val response = subject.perform(get(
          "/applications/test-app/pipelines?limit=2&expand=false&statuses=RUNNING,SUSPENDED,PAUSED,NOT_STARTED")
        ).andReturn().response
        val results = OrcaObjectMapper.getInstance().readValue(response.contentAsString, object : TypeReference<List<PipelineExecution>>() {})
        val expectedOutput = listOf("1-exec-id-3","2-exec-id-1")
        expectThat(results.size).isEqualTo(2)
        results.forEach {
          assert(it.id in expectedOutput)
        }
      }
    }

    context("test query having explicit query timeouts") {
      fixture {
        Fixture(true)
      }

      before { setup() }
      after { cleanUp() }

      test("it returns a DataAccessException on query timeout") {
        expectCatching {
          database.context.select(field("sleep(10)")).queryTimeout(1).execute()
        }
          .isFailure()
          .isA<DataAccessException>()
      }
    }
  }
}
