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
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.nhaarman.mockito_kotlin.*
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.mockito.Mockito
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
      val mapper = ExecutionMapper(mapper = ObjectMapper(), stageBatchSize = 200, compressionProperties, true)
      val mockedExecution = mock<PipelineExecution>()
      val database: DSLContext = Mockito.mock(DSLContext::class.java, Mockito.RETURNS_DEEP_STUBS)

      test("conversion ignored when trigger is not PipelineRef") {
        doReturn(DefaultTrigger(type = "default")).`when`(mockedExecution).trigger
        mapper.convertPipelineRefTrigger(mockedExecution, database)
        verify(mockedExecution, times(1)).trigger
      }

    }
  }
}
