package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesDescription
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.GetTasksResponse

class TerminateDcosInstancesAtomicOperationSpec extends BaseSpecification {
    DCOS dcosClient = Mock(DCOS)

    DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
    }

    def setup() {
        Task task = Mock(Task)
        TaskRepository.threadLocalTask.set(task)
    }

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given instanceIds'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials, dcosCluster: DEFAULT_REGION,
                instanceIds: ["TASK ONE", "TASK TWO"])
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteTask(false, _)
    }
}
