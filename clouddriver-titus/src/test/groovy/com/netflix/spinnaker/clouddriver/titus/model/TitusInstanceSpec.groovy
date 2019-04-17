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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import spock.lang.Specification

class TitusInstanceSpec extends Specification {

  Date launchDate = new Date()
  Job job = new Job(
    id: '1234',
    name: 'api-test-v001',
    applicationName: 'api.server',
    version: 'v4321',
    cpu: 1,
    memory: 2000,
    disk: 5000,
    ports: [7150] as int[],
    environment: [account: 'test', region: 'us-east-1'],
  )
  Task task = new Task(
    id: '5678',
    state: TaskState.RUNNING,
    submittedAt: launchDate,
    region: 'us-east-1',
    host: 'ec2-1-2-3-4.compute-1.amazonaws.com',
    data: [ipAddresses: [nfvpc: '4.5.6.7'], NetworkConfiguration: [EniIPAddress: '1.2.3.4']]
  )

  void 'valid titus instance is created from a titus task'() {

    when:
    TitusInstance titusInstance = new TitusInstance(job, task)

    then:

    titusInstance.id == task.id
    titusInstance.jobId == job.id
    titusInstance.jobName == job.name
    titusInstance.application == Names.parseName(job.name).app
    titusInstance.image?.dockerImageName == job.applicationName
    titusInstance.image?.dockerImageVersion == job.version
    titusInstance.state == task.state
    titusInstance.placement?.account == job.environment.account
    titusInstance.placement?.region == task.region
    titusInstance.placement?.zone == task.zone
    titusInstance.placement?.host == task.host
    titusInstance.placement?.containerIp == task.data.ipAddresses.nfvpc
    titusInstance.resources?.cpu == job.cpu
    titusInstance.resources?.memory == job.memory
    titusInstance.resources?.disk == job.disk
    titusInstance.resources?.ports == [7150: 7150]
    titusInstance.env?.account == 'test'
    titusInstance.submittedAt == task.submittedAt.time
    titusInstance.finishedAt == null
    titusInstance.privateIpAddress == task.data.ipAddresses.nfvpc
  }

  void 'can handle null ports'() {
    when:
    job.setPorts(null)
    TitusInstance titusInstance = new TitusInstance(job, task)
    then:
    titusInstance.resources.ports == [:]
  }

}
