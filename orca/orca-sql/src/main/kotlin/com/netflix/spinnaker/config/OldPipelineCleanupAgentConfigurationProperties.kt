/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("pollers.old-pipeline-cleanup")
class OldPipelineCleanupAgentConfigurationProperties() {
  /**
   * How often the agent runs, in millis
   */
  var intervalMs: Long = 3600000

  /**
   * Maximum age of pipelines to keep (if more than minimumPipelineExecutions exist)
   */
  var thresholdDays: Long = 30

  /**
   * Always keep this number of pipelines older than threshold
   */
  var minimumPipelineExecutions: Int = 5

  /**
   * Chunk size for SQL operations
   */
  var chunkSize: Int = 1

  /**
   * Application names that have special threshold during cleanup (e.g. for compliance reasons)
   */
  var exceptionalApplications: List<String> = emptyList()

  /**
   * Maximum age of pipelines to keep for exceptional applications
   */
  var exceptionalApplicationsThresholdDays: Long = 365

  constructor(
    intervalMs: Long,
    thresholdDays: Long,
    minimumPipelineExecutions: Int,
    chunkSize: Int,
    exceptionalApplications: List<String>,
    exceptionalApplicationsThresholdDays: Long
  ) : this() {
    this.intervalMs = intervalMs
    this.thresholdDays = thresholdDays
    this.minimumPipelineExecutions = minimumPipelineExecutions
    this.chunkSize = chunkSize
    this.exceptionalApplications = exceptionalApplications
    this.exceptionalApplicationsThresholdDays = exceptionalApplicationsThresholdDays
  }
}
