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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.CompressionType
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ReplicationLagAwareRepository
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.times
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.mockito.Mockito
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.sql.ResultSet
import java.time.Instant
import java.util.Optional
import java.util.zip.DeflaterOutputStream

class ExecutionMapperTest : JUnit5Minutests {

  fun tests() = rootContext<Unit> {
    val compressionProperties = ExecutionCompressionProperties().apply {
      enabled = true
    }

    context("handle body decompression") {
      val mapper = ExecutionMapper(mapper = ObjectMapper(), stageBatchSize = 200, compressionProperties, false)

      val mockedResultSet = mock<ResultSet>()

      test("decompression ignored when compressed body is null") {
        doReturn("12345").`when`(mockedResultSet).getString("body")
        doReturn(null).`when`(mockedResultSet).getBytes("compressedBody")
        doReturn(CompressionType.ZLIB.toString())
          .`when`(mockedResultSet).getString("compression_type")
        assertThat(mapper.getDecompressedBody(mockedResultSet)).isEqualTo("12345")
      }

      test("decompression performed when compressed body is not null") {
        val compressedBodyByteStream = ByteArrayOutputStream()
        DeflaterOutputStream(compressedBodyByteStream).bufferedWriter(StandardCharsets.UTF_8).use {
          it.write("12345", 0, 5)
        }
        doReturn("").`when`(mockedResultSet).getString("body")
        doReturn(compressedBodyByteStream.toByteArray())
          .`when`(mockedResultSet).getBytes("compressed_body")
        doReturn(CompressionType.ZLIB.toString())
          .`when`(mockedResultSet).getString("compression_type")
        assertThat(mapper.getDecompressedBody(mockedResultSet)).isEqualTo("12345")
      }
    }

    context("handle PipelineRef conversion") {
      val compressionPropertiesDisabled = ExecutionCompressionProperties().apply {
        enabled = false
      }
      val database: DSLContext = Mockito.mock(DSLContext::class.java, Mockito.RETURNS_DEEP_STUBS)

      test("conversion ignored when trigger is not PipelineRef") {
        val mockedExecution = mock<PipelineExecution>()
        val mapper = ExecutionMapper(ObjectMapper(), 200, compressionPropertiesDisabled, true)
        val spyMapper = Mockito.spy(mapper)

        doReturn(DefaultTrigger(type = "default")).`when`(mockedExecution).trigger
        spyMapper.convertPipelineRefTrigger(mockedExecution, database)
        verify(mockedExecution, times(1)).trigger
        verify(spyMapper, times(0)).fetchParentExecution(any(), any(), any())
      }

      test("conversion is aborted when trigger is PipelineRef but parentExecution not found") {
        val mockedExecution = mock<PipelineExecution>()
        val mapper = ExecutionMapper(ObjectMapper(), 200, compressionPropertiesDisabled, true)
        val spyMapper = Mockito.spy(mapper)

        doReturn(PipelineRefTrigger(parentExecutionId = "test-parent-id")).`when`(mockedExecution).trigger
        doReturn(ExecutionType.PIPELINE).`when`(mockedExecution).type
        doReturn(null).`when`(spyMapper).fetchParentExecution(any(), any(), any())
        spyMapper.convertPipelineRefTrigger(mockedExecution, database)
        verify(mockedExecution, times(1)).trigger
        verify(spyMapper, times(1)).fetchParentExecution(any(), any(), any())
      }

      test("conversion is processed when trigger is PipelineRef") {
        val correlationId = "test-correlation"
        val parentExecutionId = "test-execution"
        val parameters = mutableMapOf<String, Any>("test-parameter" to "test-body")
        val artifacts = mutableListOf(Artifact.builder().build())
        val resolvedExpectedArtifact = mutableListOf(ExpectedArtifact.builder().boundArtifact(Artifact.builder().build()).build())
        val otherTest = mutableMapOf<String, Any>("test-other" to "other-body")

        val execution = PipelineExecutionImpl(ExecutionType.PIPELINE, "test-app").apply {
          trigger = PipelineRefTrigger(
            correlationId = correlationId,
            parentExecutionId = parentExecutionId,
            parameters = parameters,
            artifacts = artifacts
          ).apply {
            resolvedExpectedArtifacts = resolvedExpectedArtifact
            other = otherTest
          }
        }

        val mockedParentExecution = mock<PipelineExecution>()
        val mapper = ExecutionMapper(ObjectMapper(), 200, compressionPropertiesDisabled, true)
        val spyMapper = Mockito.spy(mapper)

        doReturn(mockedParentExecution).`when`(spyMapper).fetchParentExecution(any(), any(), any())

        spyMapper.convertPipelineRefTrigger(execution, database)

        expectThat(execution.trigger) {
          isA<PipelineTrigger>()
          get { this.correlationId }.isEqualTo(correlationId)
          get { this.parameters }.isEqualTo(parameters)
          get { this.artifacts }.isEqualTo(artifacts)
          get { this.resolvedExpectedArtifacts }.isEqualTo(resolvedExpectedArtifact)
          get { this.other }.isEqualTo(otherTest)
          get { this.notifications }.isEmpty()
        }

        expectThat(execution.trigger as PipelineTrigger)
          .get(PipelineTrigger::parentExecution).isEqualTo(mockedParentExecution)

        verify(spyMapper, times(1)).fetchParentExecution(any(), any(), any())
      }
    }

    context("when the latest version of an execution is required") {
      val givenUpdatedAt = Instant.ofEpochMilli(10000L)
      val olderUpdatedAt = 5000L
      val newerUpdatedAt = 20000L
      val mapper = ExecutionMapper(
        mapper = ObjectMapper(),
        stageBatchSize = 200,
        compressionProperties = compressionProperties,
        pipelineRefEnabled = false,
        replicationLagAwareRepository = Optional.of(mock<ReplicationLagAwareRepository>())
      )
      val mockedResultSet = mock<ResultSet>()

      test("version is valid for an execution when updated_at is newer than the given value") {
        doReturn("12345").`when`(mockedResultSet).getString("body")
        doReturn(newerUpdatedAt).`when`(mockedResultSet).getLong("updated_at")
        assertThat(mapper.isUpToDateVersion(mockedResultSet, givenUpdatedAt)).isTrue()
      }

      test("version is valid for a compressed execution when both updated_at values are newer than the given value") {
        doReturn("").`when`(mockedResultSet).getString("body")
        doReturn(newerUpdatedAt).`when`(mockedResultSet).getLong("updated_at")
        doReturn(newerUpdatedAt).`when`(mockedResultSet).getLong("compressed_updated_at")
        assertThat(mapper.isUpToDateVersion(mockedResultSet, givenUpdatedAt)).isTrue()
      }

      test("version is not valid for an execution when updated_at is older than the given value") {
        doReturn("12345").`when`(mockedResultSet).getString("body")
        doReturn(olderUpdatedAt).`when`(mockedResultSet).getLong("updated_at")
        assertThat(mapper.isUpToDateVersion(mockedResultSet, givenUpdatedAt)).isFalse()
      }

      test("version is not valid for a compressed execution when updated_at is older than the given value") {
        doReturn("").`when`(mockedResultSet).getString("body")
        doReturn(olderUpdatedAt).`when`(mockedResultSet).getLong("updated_at")
        doReturn(newerUpdatedAt).`when`(mockedResultSet).getLong("compressed_updated_at")
        assertThat(mapper.isUpToDateVersion(mockedResultSet, givenUpdatedAt)).isFalse()
      }

      test("version is not valid for a compressed execution when compressed_updated_at is older than the given value") {
        doReturn("").`when`(mockedResultSet).getString("body")
        doReturn(newerUpdatedAt).`when`(mockedResultSet).getLong("updated_at")
        doReturn(olderUpdatedAt).`when`(mockedResultSet).getLong("compressed_updated_at")
        assertThat(mapper.isUpToDateVersion(mockedResultSet, givenUpdatedAt)).isFalse()
      }
    }

    context("return result codes") {
      val pipelineExecutionResultSet = mock<ResultSet>()
      val stageExecutionResultSet = mock<ResultSet>()
      val objectMapper = OrcaObjectMapper.getInstance()
      val pipelineExecutionId = ULID().nextULID()
      val pipelineExecution = PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineExecutionId, "myapp")
      val pipelineExecutionString = objectMapper.writeValueAsString(pipelineExecution)
      val stageExecution = StageExecutionImpl(pipelineExecution, "test", "test stage", mutableMapOf())
      val stageExecutionString = objectMapper.writeValueAsString(stageExecution)
      val updatedAt = Instant.ofEpochMilli(1000L)

      val mockedContext = mockk<DSLContext>()
      // Mock the DSLContext.selectExecutionStages extension function
      every {
        mockedContext.selectExecutionStages(ExecutionType.PIPELINE, listOf(pipelineExecutionId), compressionProperties)
      } returns stageExecutionResultSet

      context("when replicationLagAwareRepository is not present") {
        val mapper = ExecutionMapper(
          mapper = objectMapper,
          stageBatchSize = 200,
          compressionProperties,
          false
        )

        test("return NOT_FOUND when given an empty ResultSet") {
          doReturn(false).`when`(pipelineExecutionResultSet).next()
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.NOT_FOUND)
        }

        test("return SUCCESS when there are no errors") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")

          // Mocked stage execution calls
          doReturn(true, false).`when`(stageExecutionResultSet).next()
          doReturn(pipelineExecutionId).`when`(stageExecutionResultSet).getString("execution_id")
          doReturn(updatedAt.toEpochMilli()).`when`(stageExecutionResultSet).getLong("updated_at")
          doReturn(stageExecutionString).`when`(stageExecutionResultSet).getString("body")

          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions.size).isEqualTo(1)
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.SUCCESS)
        }
      }

      context("when replicationLagAwareRepository is present") {
        val replicationLagAwareRepository = mock<ReplicationLagAwareRepository>()
        val mapper = ExecutionMapper(
          mapper = objectMapper,
          stageBatchSize = 200,
          compressionProperties = compressionProperties,
          pipelineRefEnabled = false,
          replicationLagAwareRepository = Optional.of(replicationLagAwareRepository)
        )

        test("return NOT_FOUND when given an empty ResultSet") {
          doReturn(false).`when`(pipelineExecutionResultSet).next()
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.NOT_FOUND)
        }

        test("return SUCCESS when there are no errors") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)
          doReturn(1).`when`(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineExecutionId)

          // Mocked stage execution calls
          doReturn(true, false).`when`(stageExecutionResultSet).next()
          doReturn(pipelineExecutionId).`when`(stageExecutionResultSet).getString("execution_id")
          doReturn(stageExecution.id).`when`(stageExecutionResultSet).getString("id")
          doReturn(updatedAt.toEpochMilli()).`when`(stageExecutionResultSet).getLong("updated_at")
          doReturn(stageExecutionString).`when`(stageExecutionResultSet).getString("body")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getStageExecutionUpdate(stageExecution.id)

          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions.size).isEqualTo(1)
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.SUCCESS)
        }

        test("return MISSING_FROM_UPDATE_TIME_REPOSITORY when a pipeline execution update is missing from the ReplicationLagAwareRepository") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")

          // when
          doReturn(null).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)

          // then
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY)
        }

        test("return MISSING_FROM_UPDATE_TIME_REPOSITORY when the number of pipeline execution stages are missing from the ReplicationLagAwareRepository") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)

          // Mocked stage execution calls
          doReturn(true, false).`when`(stageExecutionResultSet).next()
          doReturn(pipelineExecutionId).`when`(stageExecutionResultSet).getString("execution_id")
          doReturn(stageExecution.id).`when`(stageExecutionResultSet).getString("id")
          doReturn(updatedAt.toEpochMilli()).`when`(stageExecutionResultSet).getLong("updated_at")
          doReturn(stageExecutionString).`when`(stageExecutionResultSet).getString("body")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getStageExecutionUpdate(stageExecution.id)

          // when
          doReturn(null).`when`(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineExecutionId)

          // then
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY)
        }

        test("return MISSING_FROM_UPDATE_TIME_REPOSITORY when a stage execution id is missing from the ReplicationLagAwareRepository") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)
          doReturn(1).`when`(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineExecutionId)

          // Mocked stage execution calls
          doReturn(true, false).`when`(stageExecutionResultSet).next()
          doReturn(pipelineExecutionId).`when`(stageExecutionResultSet).getString("execution_id")
          doReturn(stageExecution.id).`when`(stageExecutionResultSet).getString("id")
          doReturn(updatedAt.toEpochMilli()).`when`(stageExecutionResultSet).getLong("updated_at")
          doReturn(stageExecutionString).`when`(stageExecutionResultSet).getString("body")

          // when
          doReturn(null).`when`(replicationLagAwareRepository).getStageExecutionUpdate(stageExecution.id)

          // then
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY)
        }

        test("return INVALID_VERSION when a pipeline execution is too old") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")

          // when
          doReturn(updatedAt.plusMillis(500L)).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)

          // then
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.INVALID_VERSION)
        }

        test("return INVALID_VERSION when the number of retrieved stages does not match the number of stages in the ReplicationLagAwareRepository") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)

          // Mocked stage execution calls
          doReturn(true, false).`when`(stageExecutionResultSet).next()
          doReturn(pipelineExecutionId).`when`(stageExecutionResultSet).getString("execution_id")
          doReturn(stageExecution.id).`when`(stageExecutionResultSet).getString("id")
          doReturn(updatedAt.toEpochMilli()).`when`(stageExecutionResultSet).getLong("updated_at")
          doReturn(stageExecutionString).`when`(stageExecutionResultSet).getString("body")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getStageExecutionUpdate(stageExecution.id)

          // when
          doReturn(2).`when`(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineExecutionId)

          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.INVALID_VERSION)
        }

        test("return INVALID_VERSION when a stage execution is too old") {
          // Mocked pipeline execution calls
          doReturn(true, false).`when`(pipelineExecutionResultSet).next()
          doReturn(updatedAt.toEpochMilli()).`when`(pipelineExecutionResultSet).getLong("updated_at")
          doReturn(pipelineExecutionString).`when`(pipelineExecutionResultSet).getString("body")
          doReturn(pipelineExecutionId).`when`(pipelineExecutionResultSet).getString("id")
          doReturn(updatedAt).`when`(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineExecutionId)
          doReturn(1).`when`(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineExecutionId)

          // Mocked stage execution calls
          doReturn(true, false).`when`(stageExecutionResultSet).next()
          doReturn(pipelineExecutionId).`when`(stageExecutionResultSet).getString("execution_id")
          doReturn(stageExecution.id).`when`(stageExecutionResultSet).getString("id")
          doReturn(updatedAt.toEpochMilli()).`when`(stageExecutionResultSet).getLong("updated_at")
          doReturn(stageExecutionString).`when`(stageExecutionResultSet).getString("body")

          // when
          doReturn(updatedAt.plusMillis(500L)).`when`(replicationLagAwareRepository).getStageExecutionUpdate(stageExecution.id)

          // then
          val (pipelineExecutions, resultCode) = mapper.map(pipelineExecutionResultSet, mockedContext)
          assertThat(pipelineExecutions).isEmpty()
          assertThat(resultCode).isEqualTo(ReplicationLagAwareResultCode.INVALID_VERSION)
        }
      }
    }
  }
}
