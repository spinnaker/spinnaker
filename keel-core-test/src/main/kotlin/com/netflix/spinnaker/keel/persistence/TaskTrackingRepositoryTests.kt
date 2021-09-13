package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.TaskStatus.SUCCEEDED
import com.netflix.spinnaker.keel.api.actuation.SubjectType.RESOURCE
import com.netflix.spinnaker.keel.test.randomString
import com.netflix.spinnaker.time.MutableClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.Clock

abstract class TaskTrackingRepositoryTests<T : TaskTrackingRepository> {

  private val clock = MutableClock()
  abstract fun factory(clock: Clock): T

  open fun T.flush() {}

  val subject by lazy { factory(clock) }

  val taskRecord1 = TaskRecord("123", "Upsert server group", RESOURCE, randomString(), randomString(), randomString())
  val taskRecord2 = TaskRecord("456", "Bake", RESOURCE, randomString(), null, null)

  @AfterEach
  fun cleanup() {
    subject.flush()
  }

  @Test
  fun `returns nothing if there are no in-progress tasks`() {
    expectThat(subject.getIncompleteTasks()).isEmpty()
  }

  @Test
  fun `in-progress tasks are returned`() {
    subject.store(taskRecord1)
    expectThat(subject.getIncompleteTasks().size).isEqualTo(1)
    expectThat(subject.getIncompleteTasks()).first().get(TaskRecord::id).isEqualTo(taskRecord1.id)
  }

  @Test
  fun `multiple tasks may be returned`() {
    subject.store(taskRecord2)
    subject.store(taskRecord1)
    expectThat(subject.getIncompleteTasks().size).isEqualTo(2)
  }

  @Test
  fun `completed tasks are not returned`() {
    subject.store(taskRecord1)
    expectThat(subject.getIncompleteTasks().size).isEqualTo(1)
    subject.updateStatus(taskRecord1.id, SUCCEEDED)
    expectThat(subject.getIncompleteTasks()).isEmpty()
  }
}
