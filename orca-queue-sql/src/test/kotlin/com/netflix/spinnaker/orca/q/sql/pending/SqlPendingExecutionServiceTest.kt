package com.netflix.spinnaker.orca.q.sql.pending

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.PendingExecutionServiceTest
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import java.util.UUID
import org.assertj.core.api.Assertions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.subject.SubjectSpek
import org.jetbrains.spek.subject.itBehavesLike

internal object SqlPendingExecutionServiceTest : SubjectSpek<SqlPendingExecutionService>({

  itBehavesLike(PendingExecutionServiceTest)

  val testDatabase = SqlTestUtil.initTcMysqlDatabase()
  val jooq = spy(testDatabase.context)
  val queue = mock<Queue>()
  val repository = mock<ExecutionRepository>()
  val clock = fixedClock()
  val registry = NoopRegistry()
  val retryProperties = RetryProperties()

  val maxDepth = 4

  val mapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
    registerSubtypes(StartExecution::class.java, RestartStage::class.java)
  }

  subject {
    SqlPendingExecutionService(
      "test",
      jooq,
      queue,
      repository,
      mapper,
      clock,
      registry,
      retryProperties,
      maxDepth
    )
  }

  val id = UUID.randomUUID().toString()
  val pipeline = pipeline {
    pipelineConfigId = id
    stage {
      refId = "1"
    }
    stage {
      refId = "2"
      requisiteStageRefIds = setOf("1")
    }
  }

  val startMessage = StartExecution(pipeline)
  val restartMessage = RestartStage(pipeline.stageByRef("2"), "fzlem@netflix.com")
  val callback = mock<(Message) -> Unit>()

  describe("maxDepth behavior") {
    given("maxDepth messages are already queued") {
      beforeGroup {
        repeat(maxDepth) {
          subject.enqueue(id, startMessage)
        }
        whenever(repository.retrieve(any(), any<String>())).thenReturn(pipeline)
      }

      on("enqueing a start message") {
        subject.enqueue(id, startMessage)

        it("queue depth is unchanged") {
          Assertions.assertThat(subject.depth(id)).isEqualTo(maxDepth)
        }

        it("cancelled the associated execution") {
          verify(repository).retrieve(any(), any<String>())
          verify(repository).store(any())
        }
      }

      on("enqueing a restart message") {
        subject.enqueue(id, restartMessage)

        it("queue depth is unchanged") {
          Assertions.assertThat(subject.depth(id)).isEqualTo(maxDepth)
        }

        it("drops message without interacting with ExecutionRepository") {
          verifyNoMoreInteractions(repository)
        }
      }

      afterGroup { subject.purge(id, callback) }
    }
  }

  describe("popping fails due to a database exception") {
    given("db is down") {
      beforeGroup {
        jooq.execute("create table backup like pending_executions")
        jooq.execute("drop table pending_executions")
      }

      on("popping a message") {
        subject.popOldest(id)

        it("requeues a StartWaitingExecutions message") {
          verify(queue).push(any(), any())
        }
      }
    }

    afterGroup {
      jooq.execute("create table pending_executions like backup")
      jooq.execute("drop table backup")
    }
  }
})
