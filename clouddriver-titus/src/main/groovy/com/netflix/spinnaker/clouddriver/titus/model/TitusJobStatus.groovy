/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState

/**
 * Titus Job Status
 */
class TitusJobStatus implements com.netflix.spinnaker.clouddriver.model.JobStatus, Serializable {

  public static final String TYPE = TitusCloudProvider.ID

  String id
  String name
  String type = TYPE
  Map env
  String location
  Long createdTime
  Long completedTime
  String provider = TYPE
  String account
  String cluster
  Instance instance
  String application
  String region
  Map<String, String> completionDetails = [:]

  JobState jobState

  TitusJobStatus(Job job, String titusAccount, String titusRegion) {
    account = titusAccount
    region = titusRegion
    id = job.id
    name = job.name
    createdTime = job.submittedAt ? job.submittedAt.time : null
    application = Names.parseName(job.name).app

    def sortedTasks = job.tasks?.sort { it.startedAt }
    Task task = sortedTasks ? sortedTasks.last() : null

    jobState = convertTaskStateToJobState(job, task)
    completionDetails = convertCompletionDetails(task)
  }

  static Map<String, String> convertCompletionDetails(Task task) {
    if (!task) {
      return [:]
    }

    return [
      message   : task.message,
      taskId    : task.id,
      instanceId: task.instanceId
    ]
  }

  static JobState convertTaskStateToJobState(Job job, Task task) {

    if (job.getJobState() in ["Accepted", "KillInitiated"]) {
      return JobState.Running
    } else if (task == null) {
      // unexpected to have no task _and_ a job in an unexpected state
      return JobState.Running
    } else {
      switch (task.state) {
        case [TaskState.DEAD, TaskState.CRASHED, TaskState.FAILED, TaskState.STOPPED]:
          JobState.Failed
          break
        case [TaskState.FINISHED]:
          JobState.Succeeded
          break
        case [TaskState.STARTING, TaskState.QUEUED, TaskState.DISPATCHED]:
          JobState.Starting
          break
      }
    }
  }

}
