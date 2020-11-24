package com.netflix.spinnaker.keel.lifecycle

import java.time.Duration

interface LifecycleMonitorRepository {

  /**
   * Returns between zero and [limit] MonitoredTasks that have not been checked (i.e. returned by this
   * method) in at least [minTimeSinceLastCheck].
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  fun tasksDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<MonitoredTask>

  /**
   * Saves task to be monitored
   */
  fun save(task: MonitoredTask)

  /**
   * Removes task from list of things to be monitored
   */
  fun delete(task: MonitoredTask)

  /**
   * Marks a failed attempt to get the status of a task
   */
  fun markFailureGettingStatus(task: MonitoredTask)

  /**
   * @return the number of tasks currently being monitored
   */
  fun numTasksMonitoring(): Int
}
