package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.scm.CommitCreatedEvent
import com.netflix.spinnaker.time.MutableClock
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

abstract class WorkQueueRepositoryTests<IMPLEMENTATION: WorkQueueRepository> {

  abstract fun createSubject(): IMPLEMENTATION

  val clock = MutableClock()
  val subject: IMPLEMENTATION by lazy { createSubject() }

  val publishedArtifact = PublishedArtifact(
    name = "test",
    type = "DEB",
    reference = "test",
    version = "1.1",
    metadata = emptyMap()
  )
  val codeEvent = CommitCreatedEvent(repoKey = "stash/project/repo", targetBranch =  "master", commitHash = "hash123")

  @Test
  fun `initial queue size is 0`() {
    expectThat(subject.queueSize()).isEqualTo(0)
  }

  @Test
  fun `can add and remove an artifact event`() {
    subject.addToQueue(publishedArtifact)
    expectThat(subject.queueSize()).isEqualTo(1)

    val pendingArtifacts = subject.removeArtifactsFromQueue(1)
    expectThat(pendingArtifacts).hasSize(1)
    expectThat(pendingArtifacts.first()).isEqualTo(publishedArtifact)
    expectThat(subject.queueSize()).isEqualTo(0)
  }

  @Test
  fun `can add and remove a code event`() {
    subject.addToQueue(codeEvent)
    expectThat(subject.queueSize()).isEqualTo(1)

    val pendingCodeEvents = subject.removeCodeEventsFromQueue(1)
    expectThat(pendingCodeEvents).hasSize(1)
    expectThat(pendingCodeEvents.first()).isEqualTo(codeEvent)
    expectThat(subject.queueSize()).isEqualTo(0)
  }
}
