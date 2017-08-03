package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.PollingDcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.Result
import spock.lang.Subject

class ResizeDcosServerGroupAtomicOperationSpec extends BaseSpecification {
  private static final APPLICATION_NAME = 'api-test-v000'

  DCOS dcosClient = Mock(DCOS)
  PollingDcosDeploymentMonitor deploymentMonitor = Mock(PollingDcosDeploymentMonitor)

  DcosAccountCredentials testCredentials = defaultCredentialsBuilder().build()

  DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
    getDcosClient(testCredentials, DEFAULT_REGION) >> dcosClient
  }

  ResizeDcosServerGroupDescription description = new ResizeDcosServerGroupDescription(
    region: DEFAULT_REGION, serverGroupName: APPLICATION_NAME, credentials: testCredentials, dcosCluster: DEFAULT_REGION, targetSize: 2
  )

  @Subject
  AtomicOperation atomicOperation = new ResizeDcosServerGroupAtomicOperation(dcosClientProvider, deploymentMonitor, description)

  def setup() {
    Task task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void 'ResizeDcosServerGroupAtomicOperation should resize the DCOS service successfully'() {
    when:
    atomicOperation.operate([])

    then:
    noExceptionThrown()
    1 * dcosClient.maybeApp(_) >> Optional.of(new App())
    1 * dcosClient.updateApp(_, _, _) >> new Result()
  }
}
