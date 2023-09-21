package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.config.RetentionProperties
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.TaskStatus.RUNNING
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.persistence.TaskForResource
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
        .set(TASK_TRACKING.ARTIFACT_VERSION, task.artifactVersion)
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
          TASK_TRACKING.RESOURCE_ID,
          TASK_TRACKING.ARTIFACT_VERSION
        )
        .from(TASK_TRACKING)
        .where(TASK_TRACKING.ENDED_AT.isNull)
        .fetch()
        .map { (taskId, taskName, subjectType, application, environmentName, resourceId, version) ->
          TaskRecord(taskId, taskName, subjectType, application, environmentName, resourceId, version)
        }
        .toSet()
    }

  override fun getTasks(resourceId: String, limit: Int): Set<TaskForResource> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          TASK_TRACKING.TASK_ID,
          TASK_TRACKING.TASK_NAME,
          TASK_TRACKING.STARTED_AT,
          TASK_TRACKING.ENDED_AT,
          TASK_TRACKING.ARTIFACT_VERSION
        )
        .from(TASK_TRACKING)
        .where(TASK_TRACKING.RESOURCE_ID.eq(resourceId))
        .limit(limit)
        .fetch()
        .map { (taskId, taskName, startedAt, endedAt, version) ->
          TaskForResource(taskId, taskName, resourceId, startedAt, endedAt, version)
        }
        .toSet()
    }

  /**
   * This is the list of tasks that we show to users in the UI.
   *
   * We only want to show one "batch" of tasks for a resource Id.
   *  Clusters have a recorded version, so a 'batch' is updates that have the same artifact_version.
   *  For resources without a version it's everything that was started within 30 seconds
   *    of the most recent task (because we launch groups of tasks at once).
   */
  override fun getLatestBatchOfTasks(resourceId: String): Set<TaskForResource> {
    val tasks = sqlRetry.withRetry(READ) {
      jooq
        .select(
          TASK_TRACKING.TASK_ID,
          TASK_TRACKING.TASK_NAME,
          TASK_TRACKING.STARTED_AT,
          TASK_TRACKING.ENDED_AT,
          TASK_TRACKING.ARTIFACT_VERSION
        )
        .from(TASK_TRACKING)
        .where(TASK_TRACKING.RESOURCE_ID.eq(resourceId))
        .orderBy(TASK_TRACKING.STARTED_AT.desc())
        .limit(20) // probably more than the max number of tasks we launch at once? may need to tune
        .fetch()
        .map { (taskId, taskName, startedAt, endedAt, version) ->
          TaskForResource(taskId, taskName, resourceId, startedAt, endedAt, version)
        }
    }

    val mostRecentStarted = tasks
      .maxByOrNull { it.startedAt }

    // we start tasks within the same 30 seconds, so find the rest of the batch
    //  that goes with the most recently completed task.
    val batchCutofTime = mostRecentStarted?.startedAt?.minusSeconds(30)

    val version = mostRecentStarted?.artifactVersion
    return tasks
      .getTaskBatch(batchCutofTime)
      .filter { task ->
        task.artifactVersion == null || task.artifactVersion == version
      }.toSet()
  }

  private fun List<TaskForResource>.getTaskBatch(cutoff: Instant?) =
    filter { it.endedAt == null || it.startedAt.isAfter(cutoff) }.toSet()

  override fun getInFlightTasks(application: String, environmentName: String): Set<TaskForResource> =
    sqlRetry.withRetry(READ) {
      jooq
        .select(
          TASK_TRACKING.TASK_ID,
          TASK_TRACKING.TASK_NAME,
          TASK_TRACKING.RESOURCE_ID,
          TASK_TRACKING.STARTED_AT,
          TASK_TRACKING.ENDED_AT,
          TASK_TRACKING.ARTIFACT_VERSION
        )
        .from(TASK_TRACKING)
        .where(TASK_TRACKING.APPLICATION.eq(application))
        .and(TASK_TRACKING.ENVIRONMENT_NAME.eq(environmentName))
        .and(TASK_TRACKING.SUBJECT_TYPE.eq(SubjectType.RESOURCE)) //todo eb: verifications/constraints as well?
        .and(TASK_TRACKING.ENDED_AT.isNull)
        .fetch()
        .map { (taskId, taskName, resourceId, startedAt, endedAt, version) ->
          TaskForResource(taskId, taskName, resourceId, startedAt, endedAt, version)
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
        .where(TASK_TRACKING.TASK_ID.eq(taskId))
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
