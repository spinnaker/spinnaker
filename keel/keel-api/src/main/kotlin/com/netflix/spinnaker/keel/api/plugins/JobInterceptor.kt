package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.actuation.Job

/**
 * Plugin interface that provides an extension hook to manipulate actuation jobs before they
 * are submitted.
 */
interface JobInterceptor {
  /**
   * Called immediately before submitting a list of jobs via [TaskLauncher.submitJob] to potentially
   * modify the list or individual jobs within it.
   */
  fun intercept(jobs: List<Job>, user: String): List<Job>
}