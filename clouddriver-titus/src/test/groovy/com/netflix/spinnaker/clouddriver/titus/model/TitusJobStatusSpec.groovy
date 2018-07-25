/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import spock.lang.Specification;

class TitusJobStatusSpec extends Specification {
  def "should support jobs with/without tasks"() {
    given:
    def jobWithoutTasks = new Job()
    jobWithoutTasks.name = "jobWithoutTasks"
    jobWithoutTasks.jobState = "Accepted"
    jobWithoutTasks.tasks = []

    def jobWithTasks = new Job()
    jobWithTasks.name = "jobWithTasks"
    jobWithTasks.jobState = "Accepted"
    jobWithTasks.tasks = [
      buildTask(1),
      buildTask(3),
      buildTask(2)
    ]

    when:
    def titusJobWithoutTasksStatus = new TitusJobStatus(jobWithoutTasks, "account", "region")
    def titusJobWithTasksStatus = new TitusJobStatus(jobWithTasks, "account", "region")

    then:
    titusJobWithoutTasksStatus.jobState == JobState.Running
    titusJobWithoutTasksStatus.completionDetails == [:]

    titusJobWithTasksStatus.jobState == JobState.Running
    titusJobWithTasksStatus.completionDetails == [
      message: "this is my task-3",
      taskId: "id-3",
      instanceId: "instance-3"
    ]
  }

  private static Task buildTask(long startedAt) {
    def task = new Task()
    task.id = "id-${startedAt}".toString()
    task.startedAt = new Date(startedAt)
    task.instanceId = "instance-${startedAt}".toString()
    task.message = "this is my task-${startedAt}".toString()

    return task
  }
}
