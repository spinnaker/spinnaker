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

  /**
   * @return a map of the given environments to a boolean indicating whether they're currently marked for deletion.
   *
   * Intended for use with GraphQL to optimize data loading.
   */
  fun bulkGetMarkedForDeletion(environments: Set<Environment>): Map<Environment, Boolean>
}
