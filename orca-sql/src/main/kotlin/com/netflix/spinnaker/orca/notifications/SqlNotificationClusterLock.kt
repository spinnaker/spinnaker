/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.notifications

import com.netflix.spinnaker.config.TransactionRetryProperties
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import org.jooq.DSLContext
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

class SqlNotificationClusterLock(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val retryProperties: TransactionRetryProperties
) : NotificationClusterLock {

  companion object {
    private val lockTable = DSL.table("notification_lock")
    private val lockField = DSL.field("lock_name")
    private val expiryField = DSL.field("expiry")

    private val log = LoggerFactory.getLogger(NotificationClusterLock::class.java)
  }

  init {
    log.info("Configured $javaClass for NotificationClusterLock")
  }

  override fun tryAcquireLock(notificationType: String, lockTimeoutSeconds: Long): Boolean {
    val now = clock.instant()

    var changed = withRetry {
      jooq.insertInto(lockTable)
        .set(lockField, notificationType)
        .set(expiryField, now.plusSeconds(lockTimeoutSeconds).toEpochMilli())
        .onDuplicateKeyIgnore()
        .execute()
    }

    if (changed == 0) {
      changed = withRetry {
        jooq.update(lockTable)
          .set(expiryField, now.plusSeconds(lockTimeoutSeconds).toEpochMilli())
          .where(
            lockField.eq(notificationType),
            expiryField.lt(now.toEpochMilli())
          )
          .execute()
      }
    }

    return changed == 1
  }

  private fun <T> withRetry(action: () -> T): T {
    val retry = Retry.of(
      "sqlWrite",
      RetryConfig.custom<T>()
        .maxAttempts(retryProperties.maxRetries)
        .waitDuration(Duration.ofMillis(retryProperties.backoffMs))
        .ignoreExceptions(SQLDialectNotSupportedException::class.java)
        .build()
    )

    return Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
  }
}
