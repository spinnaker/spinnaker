/*
 * Copyright 2018 Cerner Corporation
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import mesosphere.dcos.client.DCOS
import mesosphere.metronome.client.model.v1.GetJobResponse
import mesosphere.metronome.client.model.v1.JobRun

class RunDcosJobAtomicOperationSpec extends BaseSpecification {
    DCOS dcosClient = Mock(DCOS)

    DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
    }

    def setup() {
        Task task = Mock(Task)
        TaskRepository.threadLocalTask.set(task)
    }

    void 'RunDcosJobAtomicOperation should trigger a new job run if the job already exists within DCOS.'() {
        setup:
        def description = new RunDcosJobDescription(credentials: testCredentials, dcosCluster: DEFAULT_REGION,
                general: new RunDcosJobDescription.GeneralSettings(id: 'testjob'))
        def atomicOperation = new RunDcosJobAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.maybeJob(_) >> Optional.of(new GetJobResponse())
        0 * dcosClient.createJob(_)
        0 * dcosClient.createJobWithSchedules(_)
        1 * dcosClient.triggerJobRun(_) >> new JobRun(jobId: 'testjob', id: 'someId')
    }

    void 'RunDcosJobAtomicOperation should create and trigger a job without a schedule if no schedule is given.'() {
        setup:
        def description = new RunDcosJobDescription(credentials: testCredentials, dcosCluster: DEFAULT_REGION,
                general: new RunDcosJobDescription.GeneralSettings(id: 'testjob'))
        def atomicOperation = new RunDcosJobAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.maybeJob(_) >> Optional.empty()
        1 * dcosClient.createJob(_)
        0 * dcosClient.createJobWithSchedules(_)
        1 * dcosClient.triggerJobRun(_) >> new JobRun(jobId: 'testjob', id: 'someId')
    }

    void 'RunDcosJobAtomicOperation should create and trigger a job with a schedule if a schedule is given.'() {
        setup:
        def description = new RunDcosJobDescription(credentials: testCredentials, dcosCluster: DEFAULT_REGION,
                general: new RunDcosJobDescription.GeneralSettings(id: 'testjob'),
                schedule: new RunDcosJobDescription.Schedule())
        def atomicOperation = new RunDcosJobAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.maybeJob(_) >> Optional.empty()
        0 * dcosClient.createJob(_)
        1 * dcosClient.createJobWithSchedules(_)
        1 * dcosClient.triggerJobRun(_) >> new JobRun(jobId: 'testjob', id: 'someId')
    }
}
