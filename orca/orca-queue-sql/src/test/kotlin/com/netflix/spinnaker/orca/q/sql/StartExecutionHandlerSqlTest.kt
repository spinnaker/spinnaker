/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.orca.q.sql

import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.CANCELED
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.q.CancelExecution
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.StartStage
import com.netflix.spinnaker.orca.q.handler.CancelExecutionHandler
import com.netflix.spinnaker.orca.q.handler.StartExecutionHandler
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import java.time.Duration
import java.time.temporal.TemporalAmount
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.context.ApplicationEventPublisher
import org.testcontainers.DockerClientFactory

class StartExecutionHandlerSqlTest {

  private lateinit var database: SqlTestUtil.TestDatabase
  private lateinit var repository: SqlExecutionRepository
  private val queue: Queue = mock()
  private val publisher: ApplicationEventPublisher = mock()

  @BeforeEach
  fun setUp() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    database = SqlTestUtil.initTcMysqlDatabase()!!
    repository = SqlExecutionRepository(
      "test",
      database.context,
      OrcaObjectMapper.newInstance(),
      RetryProperties(),
      10,
      100,
      "poolName",
      "readPoolName",
      null,
      emptyList(),
      ExecutionCompressionProperties().apply { enabled = false },
      false,
      mock<DataSource>()
    )
  }

  @AfterEach
  fun tearDown() {
    SqlTestUtil.cleanupDb(database.context)
    database.close()
  }

  @Test
  fun `a cancellation made while StartStage is being pushed survives the failed push`() {
    val execution = pipeline {
      application = "orca"
      stage {
        refId = "1"
        type = singleTaskStage.type
      }
    }
    repository.store(execution)

    // pushing StartStage fails after the execution was moved to RUNNING, and the execution is
    // canceled before the handler restores it to NOT_STARTED
    val failingQueue = FailingStartStageQueue(queue)
    val worker = Thread {
      runCatching { startHandler(failingQueue).handle(StartExecution(execution)) }
    }
    worker.start()
    failingQueue.awaitPushing()

    CancelExecutionHandler(queue, repository, publisher)
      .handle(CancelExecution(execution, "user", "reason"))

    failingQueue.fail()
    worker.join(SECONDS.toMillis(30))

    // the StartExecution message is redelivered after the ack timeout
    startHandler(queue).handle(StartExecution(execution))

    val stored = repository.retrieve(PIPELINE, execution.id)
    assertThat(stored.status).isEqualTo(CANCELED)
    assertThat(stored.isCanceled).isTrue()
  }

  private fun startHandler(queue: Queue) =
    StartExecutionHandler(queue, repository, mock<PendingExecutionService>(), publisher, Clock.systemUTC())

  /** Blocks the first push of [StartStage] until [fail] is called, then throws. */
  private class FailingStartStageQueue(private val delegate: Queue) : Queue by delegate {
    private val pushing = CountDownLatch(1)
    private val failNow = CountDownLatch(1)

    override fun push(message: Message) = push(message, Duration.ZERO)

    override fun push(message: Message, delay: TemporalAmount) {
      if (message is StartStage) {
        pushing.countDown()
        failNow.await(30, SECONDS)
        throw IllegalStateException("queue unavailable")
      }
      delegate.push(message, delay)
    }

    fun awaitPushing() = check(pushing.await(30, SECONDS)) { "StartStage was never pushed" }

    fun fail() = failNow.countDown()
  }
}
