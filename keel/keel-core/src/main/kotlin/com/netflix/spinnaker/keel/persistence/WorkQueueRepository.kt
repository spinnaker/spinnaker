package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.scm.CodeEvent

/**
 * A shared work queue for processing artifacts to help handle spiky loads of incoming artifacts
 */
interface WorkQueueRepository {

  /**
   * Adds an artifact to the work queue for processing
   */
  fun addToQueue(artifactVersion: PublishedArtifact)

  /**
   * Adds a code event to the work queue for processing
   */
  fun addToQueue(codeEvent: CodeEvent)

  /**
   * Returns up to the specified [count] of artifacts to process, and removes them from the queue.
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  fun removeArtifactsFromQueue(limit: Int = 1): List<PublishedArtifact>

  /**
   * Returns up to the specified [count] of events to process,and removes them from the queue.
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  fun removeCodeEventsFromQueue(limit: Int = 1): List<CodeEvent>

  fun queueSize(): Int
}
