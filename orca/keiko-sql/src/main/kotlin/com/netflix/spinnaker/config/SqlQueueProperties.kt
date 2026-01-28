package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import java.time.Duration
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("keiko.queue.sql")
@Validated
class SqlQueueProperties {
  /**
   * Enables use of the SqlQueue implementation, disabling RedisQueue.
   */
  var enabled: Boolean = false

  /**
   * [queueName] namespaces the database tables so that multiple keiko queues can be collocated
   * on the same database. When using a globally writeable data store with queue processors in
   * multiple regions, it may be desirable to use the region as the [queueName]. Must match
   * the regexp `\w+`.
   */
  @Pattern(regexp = "^\\w+$")
  var queueName: String = "default"

  /**
   * [deadLetterQueueName] defaults to [queueName] but can be set independently so that multiple
   * queues on the same database can potentially share a DLQ. Must match regexp "\w+".
   */
  @Pattern(regexp = "^\\w+$")
  var deadLetterQueueName: String = queueName

  /**
   * The length of time in seconds that a handler has to complete processing a message, and
   * acknowledge that processing has been completed. Messages that are not completed within
   * [ackTimeoutSeconds] are returned to the queue, up-to [Queue.maxRetries] times.
   */
  var ackTimeout: Duration = Duration.ofMinutes(2)

  /**
   * The length of time in seconds that a message with a locked set on the queue table has to
   * be moved to the unacked table, signifying that it is actively being processed. Messages
   * that are not moved from queued to unacked in [lockTtlSeconds] will have the lock released.
   */
  @Positive(message = "lockTtlSeconds must be a positive integer")
  val lockTtlSeconds: Int = 20

  /**
   * [SqlRetryProperties] determines how read and write database queries are retried.
   * See: https://github.com/spinnaker/kork/blob/master/kork-sql/src/main/kotlin/com/netflix/spinnaker/kork/sql/config/SqlRetryProperties.kt
   */
  var retries: SqlRetryProperties = SqlRetryProperties(
    transactions = RetryProperties(maxRetries = 20, backoffMs = 100),
    reads = RetryProperties()
  )
}
