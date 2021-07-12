package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.scm.CodeEvent
import com.netflix.spinnaker.keel.artifacts.WorkQueueEventType
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.persistence.WorkQueueRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.WORK_QUEUE
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.time.Clock

class SqlWorkQueueRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val mapper: ObjectMapper,
  private val sqlRetry: SqlRetry
) : WorkQueueRepository {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun addToQueue(codeEvent: CodeEvent) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(WORK_QUEUE)
        .set(WORK_QUEUE.UID, randomUID().toString())
        .set(WORK_QUEUE.TYPE, WorkQueueEventType.CODE.name)
        .set(WORK_QUEUE.FIRST_SEEN, clock.instant())
        .set(WORK_QUEUE.JSON, mapper.writeValueAsString(codeEvent))
        .execute()
    }
  }

  override fun removeCodeEventsFromQueue(limit: Int): List<CodeEvent> =
    sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(WORK_QUEUE.UID, WORK_QUEUE.JSON)
          .from(WORK_QUEUE)
          .where(WORK_QUEUE.TYPE.eq(WorkQueueEventType.CODE.name))
          .orderBy(WORK_QUEUE.FIRST_SEEN)
          .limit(limit)
          .fetch { (uid, json) ->
            deleteFrom(WORK_QUEUE)
              .where(WORK_QUEUE.UID.eq(uid))
              .execute()

            try {
              mapper.readValue<CodeEvent>(json)
            } catch (e: JsonMappingException) {
              log.warn("Unable to parse queued code event, ignoring: {}", json)
              null
            }
          }.filterNotNull()
      }
    }

  override fun addToQueue(artifactVersion: PublishedArtifact) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(WORK_QUEUE)
        .set(WORK_QUEUE.UID, randomUID().toString())
        .set(WORK_QUEUE.TYPE, WorkQueueEventType.ARTIFACT.name)
        .set(WORK_QUEUE.FIRST_SEEN, clock.instant())
        .set(WORK_QUEUE.JSON, mapper.writeValueAsString(artifactVersion))
        .execute()
    }
  }

  override fun removeArtifactsFromQueue(limit: Int): List<PublishedArtifact> =
    sqlRetry.withRetry(WRITE) {
      jooq.inTransaction {
        select(WORK_QUEUE.UID, WORK_QUEUE.JSON)
          .from(WORK_QUEUE)
          .where(WORK_QUEUE.TYPE.eq(WorkQueueEventType.ARTIFACT.name))
          .orderBy(WORK_QUEUE.FIRST_SEEN)
          .limit(limit)
          .fetch { (uid, json) ->
            deleteFrom(WORK_QUEUE)
              .where(WORK_QUEUE.UID.eq(uid))
              .execute()

            try {
              mapper.readValue<PublishedArtifact>(json)
            } catch (e: JsonMappingException) {
              log.warn("Unable to parse queued published artifact, ignoring: {}", json)
              null
            }
          }.filterNotNull()
      }
    }

  override fun queueSize(): Int =
    sqlRetry.withRetry(RetryCategory.READ) {
      jooq.fetchCount(WORK_QUEUE)
    }
}
