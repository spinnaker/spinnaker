package com.netflix.spinnaker.q.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.hash.Hashing
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.sql.util.createTableLike
import com.netflix.spinnaker.q.sql.util.excluded
import de.huxhorn.sulky.ulid.ULID
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import java.lang.Exception
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory

class SqlDeadMessageHandler(
  deadLetterQueueName: String,
  schemaVersion: Int,
  private val jooq: DSLContext,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties,
  private val ULID: ULID = ULID()
) : DeadMessageCallback {

  companion object {
    @Suppress("UnstableApiUsage")
    private val hashObjectMapper = ObjectMapper().copy().apply {
      enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    }

    private val nameSanitization =
      """[^A-Za-z0-9_]""".toRegex()

    private val log = LoggerFactory.getLogger(SqlDeadMessageHandler::class.java)
  }

  private val dlqBase = "keiko_v${schemaVersion}_dlq"
  private val dlqTableName = "${dlqBase}_${deadLetterQueueName.replace(nameSanitization, "_")}"

  private val dlqTable = DSL.table(dlqTableName)
  private val idField = DSL.field("id")
  private val fingerprintField = DSL.field("fingerprint")
  private val updatedAtField = DSL.field("updated_at")
  private val bodyField = DSL.field("body")

  init {
    initTables()
  }

  override fun invoke(queue: Queue, message: Message) {
    var fingerprint: String? = null
    var json: String? = null

    try {
      /* Storing the fingerprint may be useful for correlating Queue log messages to DLQ rows */
      fingerprint = message.fingerprint()
      json = hashObjectMapper.writeValueAsString(message)
      val ulid = ULID.nextValue().toString()

      withRetry {
        jooq.insertInto(dlqTable)
          .set(idField, ulid)
          .set(fingerprintField, fingerprint)
          .set(updatedAtField, clock.millis())
          .set(bodyField, json)
          .run {
            when (jooq.dialect()) {
              SQLDialect.POSTGRES ->
                onConflict(fingerprintField)
                  .doUpdate()
                  .set(updatedAtField, clock.millis())
                  .set(bodyField, excluded(bodyField) as Any)
                  .execute()
              else ->
                onDuplicateKeyUpdate()
                  .set(updatedAtField, MySQLDSL.values(updatedAtField) as Any)
                  .set(bodyField, MySQLDSL.values(bodyField) as Any)
                  .execute()
            }
          }
      }
    } catch (e: Exception) {
      log.error("Failed to deadLetter message, fingerprint: $fingerprint, message: $json", e)
    }
  }

  private fun initTables() {
    createTableLike(dlqTableName, "${dlqBase}_template", jooq)
  }

  @Suppress("UnstableApiUsage")
  fun Message.fingerprint() =
    hashObjectMapper.convertValue(this, MutableMap::class.java)
      .apply { remove("attributes") }
      .let {
        Hashing
          .murmur3_128()
          .hashString(
            "v2:${hashObjectMapper.writeValueAsString(it)}", StandardCharsets.UTF_8
          )
          .toString()
      }

  private fun <T> withRetry(action: () -> T): T {
    val retry = Retry.of(
      "sqlWrite",
      RetryConfig.custom<T>()
        .maxAttempts(sqlRetryProperties.transactions.maxRetries)
        .waitDuration(Duration.ofMillis(sqlRetryProperties.transactions.backoffMs))
        .ignoreExceptions(SQLDialectNotSupportedException::class.java)
        .build()
    )

    return Try.ofSupplier(Retry.decorateSupplier(retry, action)).get()
  }
}
