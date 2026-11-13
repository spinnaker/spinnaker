/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.q.sql

import com.google.common.hash.Hashing
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.EnumTestMessage
import com.netflix.spinnaker.q.EnumTestMessage.ExecutionType.PIPELINE
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class SqlQueueFingerprintTest {
  private val historicalFingerprint = Hashing.murmur3_128().hashString(
    """v2:{"ackTimeoutMs":null,"executionType":"PIPELINE","kind":"enumTest"}""",
    UTF_8
  ).toString()
  private val jooq = mock<DSLContext>().apply {
    whenever(dialect()).thenReturn(SQLDialect.MYSQL)
  }

  @Test
  fun `SQL queue preserves Jackson 2 enum fingerprints`() {
    val queue = SqlQueue(
      queueName = "fingerprint",
      schemaVersion = 1,
      jooq = jooq,
      clock = Clock.systemUTC(),
      lockTtlSeconds = 10,
      mapper = JsonMapper.builder().build(),
      serializationMigrator = Optional.empty(),
      deadMessageHandlers = emptyList(),
      publisher = mock(),
      sqlRetryProperties = SqlRetryProperties()
    )

    with(queue) {
      val message = EnumTestMessage(PIPELINE)
      assertThat(message.hashV2()).isEqualTo(historicalFingerprint)
      message.setAttribute(AttemptsAttribute(2))
      assertThat(message.hashV2()).isEqualTo(historicalFingerprint)
    }
  }

  @Test
  fun `SQL dead letter handler preserves Jackson 2 enum fingerprints`() {
    val handler = SqlDeadMessageHandler(
      deadLetterQueueName = "fingerprint",
      schemaVersion = 1,
      jooq = jooq,
      clock = Clock.systemUTC(),
      sqlRetryProperties = SqlRetryProperties()
    )

    with(handler) {
      val message = EnumTestMessage(PIPELINE)
      assertThat(message.fingerprint()).isEqualTo(historicalFingerprint)
      message.setAttribute(AttemptsAttribute(2))
      assertThat(message.fingerprint()).isEqualTo(historicalFingerprint)
    }
  }
}
