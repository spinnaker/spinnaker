package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.TaskStatus.SUCCEEDED
import com.netflix.spinnaker.keel.api.TaskStatus.TERMINAL
import com.netflix.spinnaker.keel.api.actuation.SubjectType.RESOURCE
import com.netflix.spinnaker.keel.test.randomString
import com.netflix.spinnaker.time.MutableClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import java.time.Clock

abstract class TaskTrackingRepositoryTests<T : TaskTrackingRepository> {

  private val clock = MutableClock()
  abstract fun factory(clock: Clock): T

  open fun T.flush() {}

  val subject by lazy { factory(clock) }

  val taskRecord1 = TaskRecord("1", "Upsert server group", RESOURCE, randomString(), randomString(), randomString(), "v1")
  val taskRecord2 = TaskRecord("2", "Bake", RESOURCE, randomString(), null, null, "v2")
  val taskRecord3 = TaskRecord("3", "Upsert server group", RESOURCE, "app", "env", "resource", "v3")

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

  @Test
  fun `fetching by batch works`() {
    // we fetch a singe batch of tasks

    subject.store(taskRecord3.copy(id = "1", name = "upsert1", artifactVersion = "v1"))
    clock.tickMinutes(2)
    subject.updateStatus("1", SUCCEEDED)
    clock.tickMinutes(2)

    subject.store(taskRecord3.copy(id = "4", name = "upsert2", artifactVersion = "v2"))
    clock.tickSeconds(1)
    subject.store(taskRecord3.copy(id = "5", name = "upsert3", artifactVersion = "v2"))
    clock.tickSeconds(1)
    subject.store(taskRecord3.copy(id = "6", name = "upsert4", artifactVersion = "v2"))

    clock.tickMinutes(2)
    subject.updateStatus("4", TERMINAL)

    //since the second 'wave' of tasks has one failed task, we fetch that whole wave
    val tasks = subject.getLatestBatchOfTasks("resource")
    expectThat(tasks).hasSize(3)
    expectThat(tasks.map { it.id }).containsExactlyInAnyOrder("4", "5", "6")
  }

  @Test
  fun `fetching empty batch works`() {
    expectCatching { subject.getLatestBatchOfTasks("resource") }
      .isSuccess()
      .isEmpty()
  }

  @Test
  fun `fetching by batch includes only tasks started within 30s for the same version`() {
    for (i in 1..5) {
      val id = "$i"
      subject.store(
        TaskRecord(
          id = id,
          name = "($i)Upsert server group",
          subjectType = RESOURCE,
          application = "app",
          environmentName = "env",
          resourceId = "resource",
          artifactVersion = "v1"
        )
      )
      clock.tickMinutes(5) // task runs for 5 minutes
      subject.updateStatus(id, SUCCEEDED)
      clock.tickMinutes(6)
    }

    val tasks = subject.getLatestBatchOfTasks("resource")
    expectThat(tasks).hasSize(1)
    expectThat(tasks.map { it.artifactVersion }).containsExactlyInAnyOrder("v1")
    expectThat(tasks.map { it.id }).containsExactlyInAnyOrder("5")
  }

  @Test
  fun `given 40 sequentially completed tasks, we only return the latest task because it doesn't have any others in its batch`() {
    for (i in 1..40) {
      val id = "$i"
      subject.store(
        TaskRecord(
          id = id,
          name = "($i)Upsert server group",
          subjectType = RESOURCE,
          application = "app",
          environmentName = "env",
          resourceId = "resource",
          artifactVersion = id
        )
      )
      clock.tickMinutes(5) // task runs for 5 minutes
      subject.updateStatus(id, SUCCEEDED)
      clock.tickDays(1)
      clock.tickMinutes((1L..1000L).random())
    }

    val tasks = subject.getLatestBatchOfTasks("resource")
    expectThat(tasks).hasSize(1)
    expectThat(tasks.map { it.id }).containsExactlyInAnyOrder("40")
  }
}
