package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.config.RetentionProperties
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.persistence.TaskRecord
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.TASK_TRACKING
import com.netflix.spinnaker.keel.sql.RetryCategory.READ
import com.netflix.spinnaker.keel.sql.RetryCategory.WRITE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.time.Instant

class SqlTaskTrackingRepository(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val sqlRetry: SqlRetry,
  private val retentionProperties: RetentionProperties
) : TaskTrackingRepository {

  override fun store(task: TaskRecord) {
    sqlRetry.withRetry(WRITE) {
      jooq.insertInto(TASK_TRACKING)
        .set(TASK_TRACKING.SUBJECT_TYPE, task.subjectType)
        .set(TASK_TRACKING.TASK_ID, task.id)
        .set(TASK_TRACKING.TASK_NAME, task.name)
        .set(TASK_TRACKING.STARTED_AT, clock.instant())
        .set(TASK_TRACKING.STATUS, RUNNING)
        .set(TASK_TRACKING.APPLICATION, task.application)
        .set(TASK_TRACKING.ENVIRONMENT_NAME, task.environmentName)
        .set(TASK_TRACKING.RESOURCE_ID, task.resourceId)
        .onDuplicateKeyIgnore()
        .execute()
    }
  }

  override fun getIncompleteTasks(): Set<TaskRecord> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          TASK_TRACKING.TASK_ID,
          TASK_TRACKING.TASK_NAME,
          TASK_TRACKING.SUBJECT_TYPE,
          TASK_TRACKING.APPLICATION,
          TASK_TRACKING.ENVIRONMENT_NAME,
          TASK_TRACKING.RESOURCE_ID
        )
        .from(TASK_TRACKING)
        .where(TASK_TRACKING.ENDED_AT.isNull)
        .fetch()
        .map { (taskId, taskName, subjectType, application, environmentName, resourceId) ->
          TaskRecord(taskId, taskName, subjectType, application, environmentName, resourceId)
        }
        .toSet()
    }

  override fun updateStatus(taskId: String, status: TaskStatus) {
    sqlRetry.withRetry(WRITE) {
      jooq.update(TASK_TRACKING)
        .set(TASK_TRACKING.STATUS, status)
        .apply {
          if (status.isComplete()) {
            set(TASK_TRACKING.ENDED_AT, clock.instant())
          }
        }
        .execute()
    }
  }

  override fun delete(taskId: String) {
    sqlRetry.withRetry(WRITE) {
      jooq.deleteFrom(TASK_TRACKING)
        .where(TASK_TRACKING.TASK_ID.eq(taskId))
        .execute()
    }
  }

  @Scheduled(cron = "0 0 7 * * *")
  fun purgeOldTaskRecords() {
    val cutoff = taskCutoff
    jooq
      .deleteFrom(TASK_TRACKING)
      .where(TASK_TRACKING.ENDED_AT.lessThan(cutoff))
      .execute()
      .also { count ->
        if (count > 0) {
          log.debug("Purged {} tasks that ended before {}", count, cutoff)
        }
      }
  }

  private val taskCutoff: Instant
    get() = clock.instant().minus(retentionProperties.tasks)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
