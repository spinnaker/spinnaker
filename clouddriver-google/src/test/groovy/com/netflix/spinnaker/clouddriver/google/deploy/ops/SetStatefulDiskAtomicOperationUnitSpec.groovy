package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.InstanceGroupManager
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeOperationRequest
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription
import com.netflix.spinnaker.clouddriver.google.compute.GoogleServerGroupManagers
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification

class SetStatefulDiskAtomicOperationUnitSpec extends Specification {

  private static final String SERVER_GROUP = "testapp-v000"
  private static final String REGION = "us-central1"
  private static final String DEVICE_NAME = "testapp-v000-001"
  private static final GoogleNamedAccountCredentials CREDENTIALS =
    new GoogleNamedAccountCredentials.Builder()
      .name("spinnaker-account")
      .credentials(new FakeGoogleCredentials())
      .build()

  Task task
  GoogleClusterProvider clusterProvider
  GoogleComputeApiFactory computeApiFactory
  GoogleServerGroupManagers serverGroupManagers
  GoogleNamedAccountCredentials credentials

  def setup() {
    task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)

    serverGroupManagers = Mock(GoogleServerGroupManagers)

    computeApiFactory = Mock(GoogleComputeApiFactory) {
      _ * createServerGroupManagers(*_) >> serverGroupManagers
    }

    clusterProvider = Mock(GoogleClusterProvider) {
      _ * getServerGroup(*_) >> new GoogleServerGroup(name: SERVER_GROUP).view
    }
  }

  void "sets stateful policy on instance group"() {
    setup:
    def description = new SetStatefulDiskDescription(
      serverGroupName: SERVER_GROUP,
      region: REGION,
      deviceName: DEVICE_NAME,
      credentials: CREDENTIALS)
    def operation = new SetStatefulDiskAtomicOperation(clusterProvider, computeApiFactory, description)
    def updateOp = Mock(GoogleComputeOperationRequest)
    def getManagerRequest = { new InstanceGroupManager() }
    _ * serverGroupManagers.get() >> getManagerRequest

    when:
    operation.operate([])

    then:
    1 * serverGroupManagers.update({
      it.getStatefulPolicy().getPreservedState().getDisks().containsKey(DEVICE_NAME)
    }) >> updateOp
    1 * updateOp.executeAndWait(task, /* phase= */ _)
  }
}
