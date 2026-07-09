/*
 * Copyright 2026 Harness, Inc.
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

import com.netflix.spinnaker.config.CompressionType
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.jooq.impl.DSL.field
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.mockito.kotlin.mock
import org.testcontainers.DockerClientFactory
import java.lang.System.currentTimeMillis
import javax.sql.DataSource

/**
 * Regression test for https://github.com/spinnaker/spinnaker/issues/7017.
 *
 * Prior to the fix, upserting a compressed execution body against a real PostgreSQL database
 * failed with:
 *
 *   org.springframework.jdbc.BadSqlGrammarException: jOOQ; bad SQL grammar [insert into
 *   pipelines_compressed_executions (id, compressed_body, compression_type) values (?, ?, ?)
 *   on conflict (id) do update set id = ?, compressed_body = ?, compression_type = ?];
 *   nested exception is org.postgresql.util.PSQLException: ERROR: column "compression_type"
 *   is of type compression_type_enum but expression is of type character varying
 *
 * Root cause: `compression_type` was bound as an untyped jOOQ bind parameter (a plain Kotlin
 * String). PostgreSQL refuses to implicitly cast an untyped bind parameter to a native enum
 * column type, though it will implicitly cast an inlined string literal. MySQL/MariaDB never
 * exhibited this bug (no native enum type strictness), which is why the existing, MySQL-only
 * SqlExecutionRepositoryTest suite never caught it.
 */
class SqlExecutionRepositoryPostgresCompressionTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    beforeAll {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    }

    after {
      SqlTestUtil.cleanupDb(database.context)
    }

    context("execution body compression against PostgreSQL") {

      val testType = ExecutionType.PIPELINE
      val testTable = testType.tableName
      val testId = "test_id"
      val testApplication = "test-application"
      val testCompressibleBody = "test_body_long_enough_to_compress"
      val testCompressiblePairs = mutableMapOf(
        field("id") to testId,
        field("application") to testApplication,
        field("body") to testCompressibleBody,
        field("build_time") to currentTimeMillis(),
        field("status") to "RUNNING"
      )

      test("upsert (INSERT) of a compressed execution body succeeds against PostgreSQL") {
        assertThatCode {
          sqlExecutionRepository.upsert(
            database.context,
            table = testTable,
            insertPairs = testCompressiblePairs,
            updatePairs = testCompressiblePairs,
            id = testId,
            enableCompression = true
          )
        }.doesNotThrowAnyException()

        val compressedExecutions = database.context
          .select(listOf(field("id"), field("compression_type")))
          .from(testTable.compressedExecTable)
          .fetch()
        assertThat(compressedExecutions).hasSize(1)
        assertThat(compressedExecutions.getValue(0, field("compression_type"))).isEqualTo("ZLIB")
      }

      test("upsert (ON CONFLICT DO UPDATE) of a compressed execution body succeeds against PostgreSQL") {
        // Initial insert.
        sqlExecutionRepository.upsert(
          database.context,
          table = testTable,
          insertPairs = testCompressiblePairs,
          updatePairs = testCompressiblePairs,
          id = testId,
          enableCompression = true
        )

        // Re-upserting the same id exercises the ON CONFLICT DO UPDATE SET path, which is
        // where the original bug manifested.
        assertThatCode {
          sqlExecutionRepository.upsert(
            database.context,
            table = testTable,
            insertPairs = testCompressiblePairs,
            updatePairs = testCompressiblePairs,
            id = testId,
            enableCompression = true
          )
        }.doesNotThrowAnyException()

        val compressedExecutions = database.context
          .select(listOf(field("id"), field("compression_type")))
          .from(testTable.compressedExecTable)
          .fetch()
        assertThat(compressedExecutions).hasSize(1)
        assertThat(compressedExecutions.getValue(0, field("compression_type"))).isEqualTo("ZLIB")
      }
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcPostgresDatabase()!!

    val testRetryProprties = RetryProperties()
    val orcaObjectMapper = OrcaObjectMapper.newInstance()
    val mockDataSource = mock<DataSource>()

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
        "myReadPoolName",
        null,
        emptyList(),
        executionCompressionPropertiesEnabled,
        false,
        mockDataSource
      )
  }
}
