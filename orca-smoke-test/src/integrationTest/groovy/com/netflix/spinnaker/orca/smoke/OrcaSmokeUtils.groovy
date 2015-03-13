/*
 * Copyright 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.smoke

import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer

class OrcaSmokeUtils  {

  // TODO: Expose string-work in PipelineStarter.createJobFrom() and use that directly from this method's call-sites?
  static String buildJobName(String applicationName, String pipelineName, String pipelineId) {
    return "Pipeline:$applicationName:$pipelineName:$pipelineId".toString()
  }

  /**
   * Make sure to set a timeout at the call-site.
   */
  static JobExecution pollUntilCompletion(JobExplorer jobExplorer, String jobName) {
    while (true) {
      // Would rather not have a sleep at all, but these are slow integration tests, not unit tests; so I think it's ok.
      // An alternative is to just repeatedly hit the jobExplorer methods which seems ugly as well.
      Thread.sleep(500)

      def jobInstance = jobExplorer.getJobInstances(jobName, 0, 1)[0]
      def jobExecutions = jobExplorer.getJobExecutions(jobInstance)

      if (jobExecutions.size != 1) {
        return null
      }

      if (![BatchStatus.STARTING, BatchStatus.STARTED, BatchStatus.STOPPING].contains(jobExecutions[0].status)) {
        return jobExecutions[0]
      }
    }
  }

}

