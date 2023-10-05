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
import com.netflix.spinnaker.orca.pipeline.persistence.NoopExecutionUpdateTimeRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import com.nhaarman.mockito_kotlin.mock
import dev.minutest.ContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.mockito.Mockito
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import retrofit2.mock.Calls
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TaskControllerSqlExecutionRepositoryTest : JUnit5Minutests {
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
      dataSource = mock(),
      executionUpdateTimeRepository = NoopExecutionUpdateTimeRepository()
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

    fun setupDefaultData() {
      setupDefaultDbData()
      Mockito.`when`(front50Service.getPipelines("test-app", false, null, null, null))
        .thenReturn(
          Calls.response(listOf(
            mapOf("id" to "1"),
            mapOf("id" to "2")))
        )

      Mockito.`when`(front50Service.getStrategies("test-app"))
        .thenReturn(Calls.response(listOf()))
    }

    fun setupDataWithFront50ServiceParams(pipelineNameFilter: String?, pipelineLimit: Int?) {
      setupDefaultDbData()
      Mockito.`when`(front50Service.getPipelines("test-app", false, null, pipelineNameFilter, pipelineLimit))
        .thenReturn(
          Calls.response(listOf(
            mapOf("id" to "1")))
        )
    }

    fun setupDefaultDbData() {
      database.context
        .insertInto(table("pipelines"),
          listOf(
            field("config_id"),
            field("id"),
            field("application"),
            field("build_time"),
            field("start_time"),
            field("body"),
            field("status"),
            field("updated_at")
          ))
        .values(
          listOf(
            "1",
            "1-exec-id-1",
            "test-app",
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(120, ChronoUnit.MINUTES).toEpochMilli(),
            clock.instant().minus(daysOfExecutionHistory, ChronoUnit.DAYS).minus(120, ChronoUnit.HOURS).toEpochMilli(),
            "{\"id\": \"1-exec-id-1\", \"type\": \"PIPELINE\", \"pipelineConfigId\": \"1\"}",
            "SUCCEEDED",
            Instant.EPOCH.toEpochMilli()
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
            "TERMINAL",
            Instant.EPOCH.toEpochMilli()
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
            "RUNNING",
            Instant.EPOCH.toEpochMilli()
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
            "NOT_STARTED",
            Instant.EPOCH.toEpochMilli()
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
            "STOPPED",
            Instant.EPOCH.toEpochMilli()
          )
        )
        .execute()
      Mockito.`when`(front50Service.getStrategies("test-app"))
        .thenReturn(Calls.response(listOf()))
    }

    fun cleanUp() {
      SqlTestUtil.cleanupDb(database.context)
    }

    fun verifyExecutionRetrieval(url: String, expectedOutput: List<String>, numPipelines: Int) {
      assertThat(database.context.fetchCount(table("pipelines"))).isEqualTo(numPipelines)
      val response = subject.perform(get(url)).andReturn().response
      val results = OrcaObjectMapper.getInstance().readValue(response.contentAsString, object : TypeReference<List<PipelineExecution>>() {})
      assertThat(results.size).isEqualTo(expectedOutput.size)
      assertThat(results.map { it.id }).containsExactlyInAnyOrderElementsOf(expectedOutput)
    }
  }

  fun tests() = rootContext<Fixture> {
    fun optimizedDescription(isOptimized:Boolean): String {
      return if (isOptimized) "[optimized]: " else "[non-optimized]: "
    }

    context("execution retrieval without optimization") {
      fixture {
        Fixture(false)
      }

      testExecutionRetrieval(optimizedDescription(false))
    }

    context("execution retrieval with optimization") {
      fixture {
        Fixture(true)
      }

      testExecutionRetrieval(optimizedDescription(true))
    }

    context("test query having explicit query timeouts") {
      fixture {
        Fixture(true)
      }

      before { setupDefaultData() }
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

  private fun ContextBuilder<Fixture>.testExecutionRetrieval(descriptionPrefix: String) {
    context("execution retrieval") {
      before { setupDefaultData() }
      after { cleanUp() }
      test(descriptionPrefix + "retrieve executions with limit = 2 & expand = false") {
        verifyExecutionRetrieval(
          "/v2/applications/test-app/pipelines?limit=2&expand=false",
          listOf("1-exec-id-2", "1-exec-id-3","2-exec-id-1"),
          5
        )
      }

      test(descriptionPrefix + "retrieve executions with limit = 2 & expand = false with statuses") {
        verifyExecutionRetrieval(
          "/v2/applications/test-app/pipelines?limit=2&expand=false&statuses=RUNNING,SUSPENDED,PAUSED,NOT_STARTED",
          listOf("1-exec-id-3","2-exec-id-1"),
          5
        )
      }
    }

    context("execution retrieval with pipelineNameFilter") {
      before { setupDataWithFront50ServiceParams("filter", null) }
      after { cleanUp() }
      test(descriptionPrefix + "passes pipelineNameFilter to front50 and only uses pipeline config ids from front 50") {
        verifyExecutionRetrieval(
          "/v2/applications/test-app/pipelines?limit=2&expand=false&pipelineNameFilter=filter",
          listOf("1-exec-id-2", "1-exec-id-3"),
          5
        );
      }
    }

    context("execution retrieval with pipelineLimit") {
      before { setupDataWithFront50ServiceParams(null, 1) }
      after { cleanUp() }
      test(descriptionPrefix + "passes pipelineLimit to front50 and only uses pipeline config ids from front 50") {
        verifyExecutionRetrieval(
          "/v2/applications/test-app/pipelines?limit=2&expand=false&pipelineLimit=1",
          listOf("1-exec-id-2", "1-exec-id-3"),
          5
        );
      }
    }

    context("execution retrieval with pipelineLimit and pipelineNameFilter") {
      before { setupDataWithFront50ServiceParams("filter", 1) }
      after { cleanUp() }
      test(descriptionPrefix + "passes pipelineLimit and pipelineNameFilter to front50 and only uses pipeline config ids from front 50") {
        verifyExecutionRetrieval(
          "/v2/applications/test-app/pipelines?limit=2&expand=false&pipelineLimit=1&pipelineNameFilter=filter",
          listOf("1-exec-id-2", "1-exec-id-3"),
          5
        );
      }
    }
  }
}
