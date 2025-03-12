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
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.times
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
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
import java.util.zip.DeflaterOutputStream


class ExecutionMapperTest : JUnit5Minutests {

  fun tests() = rootContext<Unit> {

    context("handle body decompression") {
      val mapper = ExecutionMapper(mapper = ObjectMapper(), stageBatchSize = 200, ExecutionCompressionProperties().apply {
        enabled = true
      }, false)

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
      val compressionProperties = ExecutionCompressionProperties().apply {
        enabled = false
      }
      val database: DSLContext = Mockito.mock(DSLContext::class.java, Mockito.RETURNS_DEEP_STUBS)

      test("conversion ignored when trigger is not PipelineRef") {
        val mockedExecution = mock<PipelineExecution>()
        val mapper = ExecutionMapper(mapper = ObjectMapper(), stageBatchSize = 200, compressionProperties = compressionProperties, true)
        val spyMapper = Mockito.spy(mapper)

        doReturn(DefaultTrigger(type = "default")).`when`(mockedExecution).trigger
        spyMapper.convertPipelineRefTrigger(mockedExecution, database)
        verify(mockedExecution, times(1)).trigger
        verify(spyMapper, times(0)).fetchParentExecution(any(), any(), any())
      }

      test("conversion is aborted when trigger is PipelineRef but parentExecution not found") {
        val mockedExecution = mock<PipelineExecution>()
        val mapper = ExecutionMapper(mapper = ObjectMapper(), stageBatchSize = 200, compressionProperties = compressionProperties, true)
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
        val mapper = ExecutionMapper(mapper = ObjectMapper(), stageBatchSize = 200, compressionProperties = compressionProperties, true)
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
  }
}
