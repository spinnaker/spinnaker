/*
 * Copyright 2017 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.model.JobStatus
import mesosphere.metronome.client.model.v1.GetJobResponse
import mesosphere.metronome.client.model.v1.JobRun
import mesosphere.metronome.client.model.v1.JobRunSummary
import org.joda.time.Instant

class DcosJobStatus implements JobStatus, Serializable {
  final String provider = DcosCloudProvider.ID

  GetJobResponse job
  JobRun jobRun
  JobRunSummary jobRunSummary
  String name
  String account
  String id
  String location
  Long createdTime
  Long completedTime
  boolean successful

  DcosJobStatus(GetJobResponse job, String jobRunId, String account, String cluster) {
    this.name = job.id
    this.id = "${jobRunId}.${job.id}".toString()
    this.location = cluster
    this.account = account
    this.job = job

    this.jobRun = job.activeRuns.find {
      jobRun -> jobRun.id == jobRunId
    }

    if (jobRun) {
      this.createdTime = Instant.parse(jobRun.createdAt).millis
      this.completedTime = null
      this.successful = false
    } else {
      this.jobRunSummary = (job.history.successfulFinishedRuns + job.history.failedFinishedRuns).find {
        jobSummary -> jobSummary.id == jobRunId
      }
    }

    if (jobRunSummary) {
      this.successful = job.history.successfulFinishedRuns.contains(this.jobRunSummary)
      this.createdTime = Instant.parse(jobRunSummary.createdAt).millis
      this.completedTime = Instant.parse(jobRunSummary.finishedAt).millis
    }
  }

  @Override
  Map<String, String> getCompletionDetails() {
    [
      successful  : successful.toString(),
      location    : location,
      jobId       : name,
      taskId      : id
    ]
  }

  @Override
  JobState getJobState() {
    if (jobRun) {
      jobRun.createdAt ? JobState.Starting : JobState.Running
    } else if (jobRunSummary) {
      successful ? JobState.Succeeded : JobState.Failed
    } else {
      JobState.Unknown
    }
  }
}
