package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Environment

/**
 * A repository that allows Keel to track environments marked for deletion.
 */
interface EnvironmentDeletionRepository : PeriodicallyCheckedRepository<Environment> {
  /**
   * Marks the specified [Environment] for deletion.
   */
  fun markForDeletion(environment: Environment)

  /**
   * @return true if the [Environment] is currently marked for deletion.
   */
  fun isMarkedForDeletion(environment: Environment): Boolean
}
