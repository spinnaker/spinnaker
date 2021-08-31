package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.config.BasePersistenceRetryConfig
import com.netflix.spinnaker.config.PersistenceRetryConfig
import com.netflix.spinnaker.keel.persistence.RetryCategory.READ
import com.netflix.spinnaker.keel.persistence.RetryCategory.WRITE
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * This class provides a consistent mechanism for retrying reads and writes
 * against a persistent data store.
 *
 * This class is functionally identical to SqlRetry in kork-sql, except that it has no SQL-specific dependencies.
 */

@EnableConfigurationProperties(PersistenceRetryConfig::class)
@Component
class PersistenceRetry (
  private val retryConfig: PersistenceRetryConfig,
  ) {
  /**
   * Retry [action] if it throws an exception
   *
   * Number of retries and backoff time depends on [category]:
   *
   * [RetryCategory.READ] -> use [readConfig]
   * [WriteCategory.READ] -> use [writeConfig]
   */
  fun <T> withRetry(category: RetryCategory, action: () -> T): T =
    when (category) {
      WRITE ->Pair("persistenceWrite", retryConfig.writes)
      READ -> Pair("persistenceRead", retryConfig.reads)
    }.let { (name, config) ->
      Retry.of(
        name,
        RetryConfig.custom<T>()
          .maxAttempts(config.maxRetries)
          .waitDuration(Duration.ofMillis(config.backoffMs))
          .build()
      )
    }.let { retry ->
      Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
    }
}

enum class RetryCategory {
  WRITE, READ
}
