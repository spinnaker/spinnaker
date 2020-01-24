package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.TaskRecord
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.TASK_TRACKING
import java.time.Clock
import org.jooq.DSLContext

class SqlTaskTrackingRepository(
  private val jooq: DSLContext,
  private val clock: Clock
) : TaskTrackingRepository {

  override fun store(task: TaskRecord) {
    jooq.insertInto(TASK_TRACKING)
      .set(TASK_TRACKING.SUBJECT, task.subject)
      .set(TASK_TRACKING.TASK_ID, task.id)
      .set(TASK_TRACKING.TASK_NAME, task.name)
      .set(TASK_TRACKING.TIMESTAMP, clock.instant().toEpochMilli())
      .onDuplicateKeyIgnore()
      .execute()
  }

  override fun getTasks(): Set<TaskRecord> {
    return jooq
      .select(TASK_TRACKING.SUBJECT, TASK_TRACKING.TASK_ID, TASK_TRACKING.TASK_NAME)
      .from(TASK_TRACKING)
      .fetch()
      .map { (resource_id, task_id, task_name) ->
        TaskRecord(task_id, task_name, resource_id)
      }
      .toSet()
  }

  override fun delete(taskId: String) {
    jooq.deleteFrom(TASK_TRACKING)
      .where(TASK_TRACKING.TASK_ID.eq(taskId))
      .execute()
  }
}
