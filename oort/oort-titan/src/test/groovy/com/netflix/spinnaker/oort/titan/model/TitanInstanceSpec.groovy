/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.oort.titan.model

import com.netflix.titanclient.model.Task
import com.netflix.titanclient.model.TaskState
import spock.lang.Specification

class TitanInstanceSpec extends Specification {

  void 'valid titan instance is created from a titan task'() {
    given:
    Date launchDate = new Date()
    Task task = new Task(
      id: '5678',
      jobId: '1234',
      state: TaskState.RUNNING,
      applicationName: 'api.server',
      version: 'v4321',
      cpu: 1,
      memory: 2000,
      disk: 5000,
      ports: [(8080): 7150],
      submittedAt: launchDate,
      env: [application: 'api', account: 'test', region: 'us-east-1', jobName: 'api-test-v000'],
      host: 'ec2-1-2-3-4.compute-1.amazonaws.com'
    )

    when:
    TitanInstance titanInstance = new TitanInstance(task)

    then:

    titanInstance.id == task.id
    titanInstance.jobId == task.jobId
    titanInstance.jobName == task.jobName
    titanInstance.application == task.application
    titanInstance.image?.dockerImageName == task.dockerImageName
    titanInstance.image?.dockerImageVersion == task.dockerImageVersion
    titanInstance.state == task.state
    titanInstance.placement?.account == task.account
    titanInstance.placement?.region == task.region
    titanInstance.placement?.subnetId == task.subnetId
    titanInstance.placement?.zone == task.zone
    titanInstance.placement?.host == task.host
    titanInstance.resources?.cpu == task.cpu
    titanInstance.resources?.memory == task.memory
    titanInstance.resources?.disk == task.disk
    titanInstance.resources?.ports == task.ports
    titanInstance.env?.application == 'api'
    titanInstance.env?.account == 'test'
    titanInstance.env?.region == 'us-east-1'
    titanInstance.submittedAt == task.submittedAt.time
    titanInstance.finishedAt == null
  }
}
