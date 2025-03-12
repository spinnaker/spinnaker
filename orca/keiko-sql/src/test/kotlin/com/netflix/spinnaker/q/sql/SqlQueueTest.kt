package com.netflix.spinnaker.q.sql

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.q.AckAttemptsAttribute
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.QueueTest
import com.netflix.spinnaker.q.TestMessage
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueueTest
import com.netflix.spinnaker.q.metrics.QueueEvent
import com.netflix.spinnaker.time.MutableClock
import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.time.Clock
import java.time.Duration
import java.util.Optional

object SqlQueueTest : QueueTest<SqlQueue>(createQueueNoPublisher, ::cleanupCallback)

object SqlMonitorableQueueTest : MonitorableQueueTest<SqlQueue>(
  ::createQueue,
  SqlQueue::retry,
  ::cleanupCallback
)

private val testDb = SqlTestUtil.initTcMysqlDatabase()
private val jooq = testDb.context

private val createQueueNoPublisher = { clock: Clock,
  deadLetterCallback: DeadMessageCallback ->
  createQueue(clock, deadLetterCallback, null)
}

private fun createQueue(clock: Clock,
                        deadLetterCallback: DeadMessageCallback,
                        publisher: EventPublisher?,
                        containsMessageBatchSize: Int = 5): SqlQueue {
  return SqlQueue(
    queueName = "test",
    schemaVersion = 1,
    jooq = jooq,
    clock = clock,
    lockTtlSeconds = 2,
    mapper = ObjectMapper().apply {
      registerModule(KotlinModule.Builder().build())
      disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

      registerSubtypes(TestMessage::class.java)
      registerSubtypes(
        MaxAttemptsAttribute::class.java,
        AttemptsAttribute::class.java,
        AckAttemptsAttribute::class.java
      )
    },
    serializationMigrator = Optional.empty(),
    ackTimeout = Duration.ofSeconds(60),
    deadMessageHandlers = listOf(deadLetterCallback),
    publisher = publisher ?: (
      object : EventPublisher {
        override fun publishEvent(event: QueueEvent) {}
      }
      ),
    sqlRetryProperties = SqlRetryProperties(
      transactions = retryPolicy,
      reads = retryPolicy
    ),
    containsMessageBatchSize = containsMessageBatchSize,
  )
}

private fun cleanupCallback() {
  SqlTestUtil.cleanupDb(jooq)
}

private val retryPolicy: RetryProperties = RetryProperties(
  maxRetries = 1,
  backoffMs = 10 // minimum allowed
)

class SqlQueueSpecificTests {
  private val batchSize = 5
  private val clock = MutableClock()
  private val deadMessageHandler: DeadMessageCallback = mock()
  private val publisher: EventPublisher = mock()
  private var queue: SqlQueue? = null

  @BeforeEach
  fun setup() {
    queue = createQueue(clock, deadMessageHandler, publisher, batchSize)
  }

  @AfterEach
  fun cleanup() {
    cleanupCallback()
  }

  @Test
  fun `doContainsMessage works with no messages present`() {
    assertThat(doContainsMessagePayload("test")).isFalse
  }

  @Test
  fun `doContainsMessage works with a single batch`() {
    pushTestMessages(batchSize)
    assertThat(doContainsMessagePayload("${batchSize-1}")).isTrue
    assertThat(doContainsMessagePayload("")).isFalse
  }

  @Test
  fun `doContainsMessage handles multiple batches during search`() {
    pushTestMessages(batchSize * 2)
    assertThat(doContainsMessagePayload("${batchSize+1}")).isTrue
    assertThat(doContainsMessagePayload("")).isFalse
  }

  private fun pushTestMessages(numberOfMessages: Int) {
    for (i in 1 .. numberOfMessages) {
      queue?.push(TestMessage(i.toString()))
    }
  }

  private fun doContainsMessagePayload(payload: String): Boolean? =
    queue?.containsMessage { message -> message is TestMessage && message.payload == payload }
}
