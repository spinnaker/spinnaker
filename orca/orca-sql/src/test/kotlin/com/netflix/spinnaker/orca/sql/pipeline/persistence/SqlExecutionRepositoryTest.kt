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
import com.netflix.spinnaker.config.CompressionMode
import com.netflix.spinnaker.config.CompressionType
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.support.TriggerDeserializer
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.sql.PipelineRefTriggerDeserializerSupplier
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import java.lang.System.currentTimeMillis
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import javax.sql.DataSource
import io.reactivex.rxjava3.core.Observable

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
        // SqlExecutionRepository.storeExecutionInternal serializes stages
        // separately, so do the same here to calculate expected sizes
        val beforeStages = pipelineExecution.stages.toList()
        pipelineExecution.stages.clear()
        val beforeTrigger =  pipelineExecution.trigger
        pipelineExecution.trigger = orcaObjectMapper.convertValue(pipelineExecution.trigger, Trigger::class.java)
        val beforePipelineString = orcaObjectMapper.writeValueAsString(pipelineExecution)
        pipelineExecution.stages.addAll(beforeStages)
        pipelineExecution.trigger = beforeTrigger
        val beforePipelineExecutionSize = beforePipelineString.length.toLong()
        val beforeStageString = orcaObjectMapper.writeValueAsString(pipelineExecution.stages.single())
        val beforeStageSize = beforeStageString.length.toLong()
        val beforeTotalSize = beforePipelineExecutionSize + beforeStageSize

        sqlExecutionRepositoryNoCompression.store(pipelineExecution)

        val afterStages = pipelineExecution.stages.toList()
        pipelineExecution.stages.clear()
        val expectedPipelineString = orcaObjectMapper.writeValueAsString(pipelineExecution)
        pipelineExecution.stages.addAll(afterStages)
        val expectedPipelineExecutionSize = expectedPipelineString.length.toLong()
        val expectedStageString = orcaObjectMapper.writeValueAsString(pipelineExecution.stages.single())
        val expectedStageSize = expectedStageString.length.toLong()
        val expectedTotalSize = expectedPipelineExecutionSize + expectedStageSize

        // Make sure the act of storing the pipeline didn't change the
        // serialization (e.g. that the size attributes don't get serialized).
        assertThat(beforePipelineString).isEqualTo(expectedPipelineString)
        assertThat(beforeStageString).isEqualTo(expectedStageString)
        assertThat(beforePipelineExecutionSize).isEqualTo(expectedPipelineExecutionSize);
        assertThat(beforeStageSize).isEqualTo(expectedStageSize);
        assertThat(beforeTotalSize).isEqualTo(expectedTotalSize);

        // And make sure the size is correct
        assertThat(pipelineExecution.size.get()).isEqualTo(expectedPipelineExecutionSize)
        assertThat(pipelineExecution.stages.single().size.get()).isEqualTo(expectedStageSize)
        assertThat(pipelineExecution.totalSize.get()).isEqualTo(expectedTotalSize)

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

        // Make sure is calculated on retrieve as well
        assertThat(actualPipelineExecution.size.get()).isEqualTo(expectedPipelineExecutionSize)
        assertThat(actualPipelineExecution.stages.single().size.get()).isEqualTo(expectedStageSize)
        assertThat(actualPipelineExecution.totalSize.get()).isEqualTo(expectedTotalSize)
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

    context("retrievePipelinesForApplication") {
      val pipelineExecution1 = PipelineExecutionImpl(ExecutionType.PIPELINE, "application-1")
      val pipelineExecution2 = PipelineExecutionImpl(ExecutionType.PIPELINE, "application-2")

      test("correctly use where clause") {
        // Store pipelines in two different applications
        sqlExecutionRepository.store(pipelineExecution1)
        sqlExecutionRepository.store(pipelineExecution2)

        val observable = sqlExecutionRepository.retrievePipelinesForApplication("application-2")
        val executions = observable.toList().blockingGet()
        assertThat(executions.map(PipelineExecution::getApplication).single()).isEqualTo("application-2")
      }
    }

    context("upserting executions with pipelineRef") {

      val testType = ExecutionType.PIPELINE
      val testTable = testType.tableName
      val testStagesTable = testType.stagesTableName
      val testApplication = "test-application"

      val parentExecutionPipeline = PipelineExecutionImpl(testType, testApplication)
      val parentExecutionId = parentExecutionPipeline.id

      val pipelineExecutionWithPipelineTrigger = PipelineExecutionImpl(testType, testApplication).also {
        it.trigger = PipelineTrigger(parentExecution = parentExecutionPipeline)
      }.apply { stage {} }
      val pipelineIdWithPipelineTrigger = pipelineExecutionWithPipelineTrigger.id

      val expectedExecutionWithPipelineRef = PipelineExecutionImpl(testType, testApplication).also {
        it.id = pipelineExecutionWithPipelineTrigger.id
        it.trigger = PipelineRefTrigger(parentExecutionId = parentExecutionId)
      }

      val pipelineExecutionWithoutTrigger = PipelineExecutionImpl(testType, testApplication).apply { stage {} }
      val pipelineIdWithoutTrigger = pipelineExecutionWithoutTrigger.id

      val pipelineExecutionWithDefaultTrigger = PipelineExecutionImpl(testType, testApplication).also {
        it.trigger = DefaultTrigger(type = "default")
      }.apply { stage {} }
      val pipelineIdWithDefaultTrigger = pipelineExecutionWithDefaultTrigger.id
      val expectedExecutionWithDefaultTrigger = PipelineExecutionImpl(testType, testApplication).also {
        it.id = pipelineExecutionWithDefaultTrigger.id
        it.trigger = DefaultTrigger(type = "default")
      }

      //store a pipeline execution that will act as parentExecution
      before {
        sqlExecutionRepositoryWithPipelineRefOnly.store(parentExecutionPipeline)
      }

      test("store execution with pipelineRef when PipelineExecution trigger is present, retrieve with full context") {
        sqlExecutionRepositoryWithPipelineRefOnly.store(pipelineExecutionWithPipelineTrigger)
        pipelineExecutionWithPipelineTrigger.trigger = PipelineTrigger(parentExecution = parentExecutionPipeline)

        //make sure the execution has a pipelineRef in database
        val expectedPipelineString = orcaObjectMapper.writeValueAsString(expectedExecutionWithPipelineRef)
        val executions = database.context.select(listOf(field("id"), field("body")))
          .from(testTable)
          .where(field("id").eq(pipelineIdWithPipelineTrigger))
          .fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("body"))).isEqualTo(expectedPipelineString)

        //make sure the execution has full context on retrieve
        val actualPipelineExecution = sqlExecutionRepositoryWithPipelineRefOnly.retrieve(testType, pipelineIdWithPipelineTrigger)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecutionWithPipelineTrigger)
      }

      test("store execution without PipelineExecution trigger, retrieve with full context") {

        val beforeStages = pipelineExecutionWithoutTrigger.stages.toList()
        pipelineExecutionWithoutTrigger.stages.clear()
        val beforeTrigger = pipelineExecutionWithoutTrigger.trigger
        pipelineExecutionWithoutTrigger.trigger = orcaObjectMapper.convertValue(pipelineExecutionWithoutTrigger.trigger, Trigger::class.java)
        val beforePipelineString = orcaObjectMapper.writeValueAsString(pipelineExecutionWithoutTrigger)
        pipelineExecutionWithoutTrigger.stages.addAll(beforeStages)
        pipelineExecutionWithoutTrigger.trigger = beforeTrigger
        val beforeStageString = orcaObjectMapper.writeValueAsString(pipelineExecutionWithoutTrigger.stages.single())
        val stageExecutionId = pipelineExecutionWithoutTrigger.stages.single().id

        sqlExecutionRepositoryWithPipelineRefOnly.store(pipelineExecutionWithoutTrigger)

        //validate pipeline execution is store properly in database
        val executions = database.context.select(listOf(field("id"), field("body")))
          .from(testTable)
          .where(field("id").eq(pipelineIdWithoutTrigger))
          .fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("body"))).isEqualTo(beforePipelineString)

        //validate stage execution is store properly in database
        val stageExecutions = database.context.select(listOf(field("id"), field("body")))
          .from(testStagesTable)
          .where(field("id").eq(stageExecutionId))
          .fetch()
        assertThat(stageExecutions).hasSize(1)
        assertThat(stageExecutions.getValue(0, field("body"))).isEqualTo(beforeStageString)

        //make sure the execution has full context on retrieve
        val actualPipelineExecution = sqlExecutionRepositoryWithPipelineRefOnly.retrieve(testType, pipelineIdWithoutTrigger)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecutionWithoutTrigger)
      }

      test("store execution with PipelineExecution trigger but pipelineRef is disabled, retrieve with full context") {

        this.addCustomDeserializerWithFeatureFlagDisabled()

        sqlExecutionRepositoryNoCompression.store(pipelineExecutionWithPipelineTrigger)

        val beforeStages = pipelineExecutionWithPipelineTrigger.stages.toList()
        pipelineExecutionWithPipelineTrigger.stages.clear()
        val beforePipelineString = orcaObjectMapper.writeValueAsString(pipelineExecutionWithPipelineTrigger)
        pipelineExecutionWithPipelineTrigger.stages.addAll(beforeStages)

        //make sure the execution is not store with pipelineRef in database
        val executions = database.context.select(listOf(field("id"), field("body")))
          .from(testTable)
          .where(field("id").eq(pipelineIdWithPipelineTrigger))
          .fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("body"))).isEqualTo(beforePipelineString)

        //make sure the execution has full context on retrieve
        val actualPipelineExecution = sqlExecutionRepositoryNoCompression.retrieve(testType, pipelineIdWithPipelineTrigger)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecutionWithPipelineTrigger)
        this.addCustomDeserializerWithFeatureFlagEnabled()
      }

      test("store execution with Default trigger, retrieve with full context") {
        sqlExecutionRepositoryWithPipelineRefOnly.store(pipelineExecutionWithDefaultTrigger)

        //make sure the execution is not store with pipelineRef in database
        expectedExecutionWithDefaultTrigger.trigger = orcaObjectMapper.convertValue(expectedExecutionWithDefaultTrigger.trigger, Trigger::class.java)
        val expectedPipelineString = orcaObjectMapper.writeValueAsString(expectedExecutionWithDefaultTrigger)
        val executions = database.context.select(listOf(field("id"), field("body")))
          .from(testTable)
          .where(field("id").eq(pipelineIdWithDefaultTrigger))
          .fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("body"))).isEqualTo(expectedPipelineString)

        //make sure the execution has full context on retrieve
        val actualPipelineExecution = sqlExecutionRepositoryWithPipelineRefOnly.retrieve(testType, pipelineIdWithDefaultTrigger)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecutionWithDefaultTrigger)
      }
    }

    context("pipelineRef can be deserialized if feature flag is disabled") {

       val testType = ExecutionType.PIPELINE
       val testTable = testType.tableName
       val testApplication = "test-application"

       val parentExecutionPipeline = PipelineExecutionImpl(testType, testApplication)
       val parentExecutionId = parentExecutionPipeline.id

       val pipelineExecutionWithPipelineTrigger = PipelineExecutionImpl(testType, testApplication).also {
         it.trigger = PipelineTrigger(parentExecution = parentExecutionPipeline)
       }.apply { stage {} }
       val pipelineIdWithPipelineTrigger = pipelineExecutionWithPipelineTrigger.id

       val expectedExecutionWithPipelineRef = PipelineExecutionImpl(testType, testApplication).also {
         it.id = pipelineExecutionWithPipelineTrigger.id
         it.trigger = PipelineRefTrigger(parentExecutionId = parentExecutionId)
       }

       before {
         sqlExecutionRepositoryWithPipelineRefOnly.store(parentExecutionPipeline) //we ensure the parentExecution exist on database
       }

       test("store execution with pipelineRef disabled, for executions stored as pipelineRef it is still able to retrieve full context") {
         //store a pipeline execution with pipelineRef first
         sqlExecutionRepositoryWithPipelineRefOnly.store(pipelineExecutionWithPipelineTrigger)
         //we restore the trigger to be a pipelineTrigger
         pipelineExecutionWithPipelineTrigger.trigger = PipelineTrigger(parentExecution = parentExecutionPipeline)

         //make sure the execution has a pipelineRef in database
         val expectedPipelineString = orcaObjectMapper.writeValueAsString(expectedExecutionWithPipelineRef)
         val executionsWithPipelineRef = database.context.select(listOf(field("id"), field("body")))
           .from(testTable)
           .where(field("id").eq(pipelineIdWithPipelineTrigger))
           .fetch()
         assertThat(executionsWithPipelineRef).hasSize(1)
         assertThat(executionsWithPipelineRef.getValue(0, field("body"))).isEqualTo(expectedPipelineString)

         //make sure the execution has full context on retrieve
         val actualPipelineExecution = sqlExecutionRepositoryWithPipelineRefOnly.retrieve(testType, pipelineIdWithPipelineTrigger)
         assertThat(actualPipelineExecution).isEqualTo(pipelineExecutionWithPipelineTrigger)

         //create another pipeline with pipelineTriggger
         val anotherPipelineExecution = PipelineExecutionImpl(testType, testApplication).also {
           it.trigger = PipelineTrigger(parentExecution = PipelineExecutionImpl(ExecutionType.PIPELINE, testApplication))
         }
         val anotherPipelineExecutionId = anotherPipelineExecution.id

         this.addCustomDeserializerWithFeatureFlagDisabled()

         //store another execution with PipelineTrigger but pipelineRef is disabled
         sqlExecutionRepositoryNoCompression.store(anotherPipelineExecution)
         val expectedAnotherPipelineString = orcaObjectMapper.writeValueAsString(anotherPipelineExecution)

         //make sure the execution is not store with pipelineRef in database
         val executions = database.context.select(listOf(field("id"), field("body")))
           .from(testTable)
           .where(field("id").eq(anotherPipelineExecutionId))
           .fetch()
         assertThat(executions).hasSize(1)
         assertThat(executions.getValue(0, field("body"))).isEqualTo(expectedAnotherPipelineString)

         //make sure the execution has full context on retrieve
         val actualAnotherPipelineExecution = sqlExecutionRepositoryNoCompression.retrieve(testType, anotherPipelineExecutionId)
         assertThat(actualAnotherPipelineExecution).isEqualTo(anotherPipelineExecution)

         //make sure the execution has full context on retrieve for a execution stored with pipelineRef
         val previousPipelineExecution = sqlExecutionRepositoryNoCompression.retrieve(testType, pipelineIdWithPipelineTrigger)
         assertThat(previousPipelineExecution).isEqualTo(pipelineExecutionWithPipelineTrigger)

         this.addCustomDeserializerWithFeatureFlagEnabled()
       }
    }

    context("execution pipelineRef and execution body compression can work together") {

      val testType = ExecutionType.PIPELINE
      val testTable = testType.tableName
      val testStagesTable = testType.stagesTableName
      val testApplication = "test-application"

      val parentExecutionPipeline = PipelineExecutionImpl(testType, testApplication)
      val parentExecutionId = parentExecutionPipeline.id

      val pipelineExecution = PipelineExecutionImpl(testType, testApplication).also {
        it.trigger = PipelineTrigger(parentExecution = parentExecutionPipeline)
      }
      val pipelineId = pipelineExecution.id

      val expectedExecutionWithPipelineRef = PipelineExecutionImpl(testType, testApplication).also {
        it.id = pipelineExecution.id
        it.trigger = PipelineRefTrigger(parentExecutionId = parentExecutionPipeline.id )
      }

      //store a pipeline execution that will act as parentExecution
      before {
        sqlExecutionRepositoryWithPipelineRefAndCompression.store(parentExecutionPipeline)
      }

      test("store execution with pipelineRef enabled and compressed enabled") {

        sqlExecutionRepositoryWithPipelineRefAndCompression.store(pipelineExecution)
        pipelineExecution.trigger = PipelineTrigger(parentExecution = parentExecutionPipeline)

        val expectedExecutionPipelineString = orcaObjectMapper.writeValueAsString(expectedExecutionWithPipelineRef)

        val testCompressedBody = sqlExecutionRepository.getCompressedBody(pipelineId, expectedExecutionPipelineString)

        val compressedExecutions = database.context.select(listOf(field("id"), field("compressed_body")))
          .from(testTable.compressedExecTable)
          .where(field("id").eq(pipelineId))
          .fetch()
        assertThat(compressedExecutions).hasSize(1)
        assertThat(compressedExecutions.getValue(0, field("compressed_body"))).isEqualTo(testCompressedBody)

        val executions = database.context.select(listOf(field("id"), field("body")))
          .from(testTable)
          .where(field("id").eq(pipelineId))
          .fetch()
        assertThat(executions).hasSize(1)
        assertThat(executions.getValue(0, field("body"))).asString().isEmpty()

        //make sure the execution has full context on retrieve for execution with pipelineRef
        val actualPipelineExecution = sqlExecutionRepositoryWithPipelineRefAndCompression.retrieve(testType, pipelineId)
        assertThat(actualPipelineExecution).isEqualTo(pipelineExecution)
      }
    }
    context("read pool usage") {
      val mockedDslContext = mock<DSLContext>()
      val mockedObjectMapper = mock<ObjectMapper>()
      val mockedAbstractRoutingDataSource = mock<AbstractRoutingDataSource>()

      test("fallback when read pool is not configured") {

        val poolName = "poolName"
        val readPoolName = "myReadPoolName"

        doReturn(mapOf("someOtherPool" to mock<DataSource>())).`when`(mockedAbstractRoutingDataSource).resolvedDataSources

        val sqlExecutionRepository = SqlExecutionRepository("test",
          mockedDslContext,
          mockedObjectMapper,
          testRetryProprties,
          10,
          100,
          poolName,
          readPoolName,
          null,
          emptyList(),
          executionCompressionPropertiesEnabled,
          false,
          mockedAbstractRoutingDataSource
        )

        verify(mockedAbstractRoutingDataSource, atLeastOnce()).resolvedDataSources
        assertThat(sqlExecutionRepository.readPoolName).isEqualTo(poolName)
      }

      test("use read pool when configured") {

        val poolName = "poolName"
        val readPoolName = "myReadPoolName"

        doReturn(mapOf(readPoolName to mock<DataSource>())).`when`(mockedAbstractRoutingDataSource).resolvedDataSources

        val sqlExecutionRepository = SqlExecutionRepository("test",
          mockedDslContext,
          mockedObjectMapper,
          testRetryProprties,
          10,
          100,
          poolName,
          readPoolName,
          null,
          emptyList(),
          executionCompressionPropertiesEnabled,
          false,
          mockedAbstractRoutingDataSource
        )

        verify(mockedAbstractRoutingDataSource, atLeastOnce()).resolvedDataSources
        assertThat(sqlExecutionRepository.readPoolName).isEqualTo(readPoolName)
      }

    }
  }

  private inner class Fixture {

    val deserializerEnabled = PipelineRefTriggerDeserializerSupplier(true)
    val deserializerDisabled = PipelineRefTriggerDeserializerSupplier(false)

    init {
      TriggerDeserializer.customTriggerSuppliers.add(deserializerEnabled)
    }

    val database = SqlTestUtil.initTcMysqlDatabase()!!

    val testRetryProprties = RetryProperties()

    val orcaObjectMapper = OrcaObjectMapper.newInstance()

    val executionCompressionPropertiesEnabled = ExecutionCompressionProperties().apply {
      enabled = true
      bodyCompressionThreshold = 9
      compressionType = CompressionType.ZLIB
    }
    val mockDataSource = mock<DataSource>()

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
        "myReadPoolName",
        null,
        emptyList(),
        executionCompressionPropertiesDisabled,
        false,
        mockDataSource
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
        "myReadPoolName",
        null,
        emptyList(),
        executionCompressionPropertiesReadOnly,
        false,
        mockDataSource
      )

    val sqlExecutionRepositoryWithPipelineRefOnly =
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
        executionCompressionPropertiesDisabled,
        true,
        mockDataSource
      )

    val sqlExecutionRepositoryWithPipelineRefAndCompression =
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
        true,
        mockDataSource
      )

    fun addCustomDeserializerWithFeatureFlagEnabled() {
      TriggerDeserializer.customTriggerSuppliers.clear()
      TriggerDeserializer.customTriggerSuppliers.add(deserializerEnabled)
    }

    fun addCustomDeserializerWithFeatureFlagDisabled() {
      TriggerDeserializer.customTriggerSuppliers.clear()
      TriggerDeserializer.customTriggerSuppliers.add(deserializerDisabled)
    }
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
