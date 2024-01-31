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
package com.netflix.spinnaker.orca.sql.pipeline.persistence

import com.netflix.spinnaker.config.CompressionMode
import com.netflix.spinnaker.config.CompressionType
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.impl.DSL.field
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import java.lang.System.currentTimeMillis

class SqlExecutionRepositoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    beforeAll {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    }

    after {
      SqlTestUtil.cleanupDb(database.context)
    }

    context("execution body compression") {
      test("Compression performed when length limit breached") {
        val compressedBody =
          sqlExecutionRepository.getCompressedBody(id = "12345", body = "12345678910")
        assert(compressedBody is ByteArray)
      }

      test("Compression ignored when length limit not breached") {
        assertThat(sqlExecutionRepository.getCompressedBody(id = "12345", body = "123456789"))
          .isEqualTo(null)
      }
    }

    context("upserting executions with body compression") {

      val testType = ExecutionType.PIPELINE
      val testTable = testType.tableName
      val testStagesTable = testType.stagesTableName
      val testId = "test_id"
      val testApplication = "test-application"
      val pipelineExecution = PipelineExecutionImpl(testType, testApplication).apply {
        stage {}
      }
      val pipelineId = pipelineExecution.id

      val testBody = "test_body" // not long enough to compress
      val testPairs = mutableMapOf(
          field("id") to testId,
          field("application") to testApplication,
          field("body") to testBody,
          field("build_time") to currentTimeMillis()
      )

      val testCompressibleBody = "test_body_long_enough_to_compress"
      val testCompressiblePairs = mutableMapOf(
          field("id") to testId,
          field("application") to testApplication,
          field("body") to testCompressibleBody,
          field("build_time") to currentTimeMillis()
      )

      test("verify assumptions") {
        // Verify that pipelineExecution is compressible
        assertThat(sqlExecutionRepository.getCompressedBody(pipelineId, orcaObjectMapper.writeValueAsString(pipelineExecution))).isNotNull()

        // And that at least one stage is compressible.  Use firstOrNull and
        // assertThat instead of first since failures are easier to identify.
        assertThat(pipelineExecution.stages.firstOrNull { stage -> (sqlExecutionRepository.getCompressedBody(stage.id, orcaObjectMapper.writeValueAsString(stage)) != null) }).isNotNull()


        // Verify that testBody is not big enough to compress
        assertThat(sqlExecutionRepository.getCompressedBody(testId, testBody)).isNull()

        // and that testCompressibleBody is
        assertThat(sqlExecutionRepository.getCompressedBody(testId, testCompressibleBody)).isNotNull()
      }

      test("Compressed upsert not performed when compression enabled, but body not big enough to compress") {
        sqlExecutionRepository.upsert(
          database.context,
          table = testTable,
          insertPairs = testPairs,
          updatePairs = testPairs,
          id = testId,
          enableCompression = true
        )

        val numCompressedExecutions = database.context.fetchCount(testTable.compressedExecTable)
        assertThat(numCompressedExecutions).isEqualTo(0)

        val executions = database.context.select(listOf(field("id"), field("body"))).from(testTable).fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("id"))).isEqualTo(testId)
        assertThat(executions.getValue(0, field("body"))).isEqualTo(testBody)
      }

      test("Compressed upsert performed when body is big enough to compress, and compression is enabled") {
        sqlExecutionRepository.upsert(
          database.context,
          table = testTable,
          insertPairs = testCompressiblePairs,
          updatePairs = testCompressiblePairs,
          id = testId,
          enableCompression = true
        )

        val testCompressedBody = sqlExecutionRepository.getCompressedBody(testId, testCompressibleBody)

        val compressedExecutions = database.context.select(listOf(field("id"), field("compressed_body"))).from(testTable.compressedExecTable).fetch()
        assertThat(compressedExecutions).hasSize(1)
        assertThat(compressedExecutions.getValue(0, field("id"))).isEqualTo(testId)
        assertThat(compressedExecutions.getValue(0, field("compressed_body"))).isEqualTo(testCompressedBody)

        val executions = database.context.select(listOf(field("id"), field("body"))).from(testTable).fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("id"))).isEqualTo(testId)
        assertThat(executions.getValue(0, field("body"))).asString().isEmpty()
      }

      test("Compressed upsert not performed when body is big enough to compress, but compression is disabled") {
        sqlExecutionRepository.upsert(
          database.context,
          table = testTable,
          insertPairs = testCompressiblePairs,
          updatePairs = testCompressiblePairs,
          id = testId,
          enableCompression = false
        )

        val numCompressedExecutions = database.context.fetchCount(testTable.compressedExecTable)
        assertThat(numCompressedExecutions).isEqualTo(0)

        val executions = database.context.select(listOf(field("id"), field("body"))).from(testTable).fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("id"))).isEqualTo(testId)
        assertThat(executions.getValue(0, field("body"))).isEqualTo(testCompressibleBody)
      }

      test("store and retrieve with compression disabled") {
        sqlExecutionRepositoryNoCompression.store(pipelineExecution)

        val numCompressedExecutions = database.context.fetchCount(testTable.compressedExecTable)
        assertThat(numCompressedExecutions).isEqualTo(0)

        val numCompressedStages = database.context.fetchCount(testStagesTable.compressedExecTable)
        assertThat(numCompressedStages).isEqualTo(0)

        val numExecutions = database.context.fetchCount(testTable)
        assertThat(numExecutions).isEqualTo(1)

        val numStages = database.context.fetchCount(testStagesTable)
        assertThat(numStages).isEqualTo(1)

        val actualPipelineExecution = sqlExecutionRepositoryNoCompression.retrieve(testType, pipelineId)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecution)
      }

      test("store compressed, retrieve with compression disabled") {
        sqlExecutionRepository.store(pipelineExecution)

        val numCompressedExecutions = database.context.fetchCount(testTable.compressedExecTable)
        assertThat(numCompressedExecutions).isEqualTo(1)

        val numCompressedStages = database.context.fetchCount(testStagesTable.compressedExecTable)
        assertThat(numCompressedStages).isEqualTo(1)

        val numExecutions = database.context.fetchCount(testTable)
        assertThat(numExecutions).isEqualTo(1)

        val numStages = database.context.fetchCount(testStagesTable)
        assertThat(numStages).isEqualTo(1)

        assertThatThrownBy { sqlExecutionRepositoryNoCompression.retrieve(testType, pipelineId) }.isInstanceOf(ExecutionNotFoundException::class.java)
      }

      test("store compressed, retrieve in read-only mode") {
        sqlExecutionRepository.store(pipelineExecution)

        val numCompressedExecutions = database.context.fetchCount(testTable.compressedExecTable)
        assertThat(numCompressedExecutions).isEqualTo(1)

        val numCompressedStages = database.context.fetchCount(testStagesTable.compressedExecTable)
        assertThat(numCompressedStages).isEqualTo(1)

        val numExecutions = database.context.fetchCount(testTable)
        assertThat(numExecutions).isEqualTo(1)

        val numStages = database.context.fetchCount(testStagesTable)
        assertThat(numStages).isEqualTo(1)

        val actualPipelineExecution = sqlExecutionRepositoryReadOnly.retrieve(testType, pipelineId)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecution)
      }

      test("In read-only mode, body big enough to compress is stored as plain text") {
        sqlExecutionRepositoryReadOnly.store(pipelineExecution)

        val numCompressedExecutions = database.context.fetchCount(testTable.compressedExecTable)
        assertThat(numCompressedExecutions).isEqualTo(0)

        val numCompressedStages = database.context.fetchCount(testStagesTable.compressedExecTable)
        assertThat(numCompressedStages).isEqualTo(0)

        val numExecutions = database.context.fetchCount(testTable)
        assertThat(numExecutions).isEqualTo(1)

        val numStages = database.context.fetchCount(testStagesTable)
        assertThat(numStages).isEqualTo(1)

        val actualPipelineExecution = sqlExecutionRepositoryReadOnly.retrieve(testType, pipelineId)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecution)
      }
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!

    val testRetryProprties = RetryProperties()

    val orcaObjectMapper = OrcaObjectMapper.newInstance()

    val executionCompressionPropertiesEnabled = ExecutionCompressionProperties().apply {
      enabled = true
      bodyCompressionThreshold = 9
      compressionType = CompressionType.ZLIB
    }

    val sqlExecutionRepository =
      SqlExecutionRepository(
        "test",
        database.context,
        orcaObjectMapper,
        testRetryProprties,
        10,
        100,
        "poolName",
        null,
        emptyList(),
        executionCompressionPropertiesEnabled
      )

    val executionCompressionPropertiesDisabled = ExecutionCompressionProperties().apply {
      enabled = false
    }

    val sqlExecutionRepositoryNoCompression =
      SqlExecutionRepository(
        "test",
        database.context,
        orcaObjectMapper,
        testRetryProprties,
        10,
        100,
        "poolName",
        null,
        emptyList(),
        executionCompressionPropertiesDisabled
      )

    val executionCompressionPropertiesReadOnly = ExecutionCompressionProperties().apply {
      enabled = true
      compressionMode = CompressionMode.READ_ONLY
      bodyCompressionThreshold = 9
      compressionType = CompressionType.ZLIB
    }

    val sqlExecutionRepositoryReadOnly =
      SqlExecutionRepository(
        "test",
        database.context,
        orcaObjectMapper,
        testRetryProprties,
        10,
        100,
        "poolName",
        null,
        emptyList(),
        executionCompressionPropertiesReadOnly
      )
  }
}

/**
 * Build a top-level stage. Use in the context of [#pipeline].  This duplicates
 * a function in orca-api-tck, but liquibase complains about duplicate schema
 * files when orca-sql depends on orca-api-tck.
 *
 * Automatically hooks up execution.
 */
private fun PipelineExecution.stage(init: StageExecution.() -> Unit): StageExecution {
  val stage = StageExecutionImpl()
  stage.execution = this
  stage.type = "test"
  stage.refId = "1"
  stages.add(stage)
  stage.init()
  return stage
}
