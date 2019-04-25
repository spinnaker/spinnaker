package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription
import com.netflix.spinnaker.clouddriver.google.deploy.instancegroups.GoogleServerGroupManagers
import com.netflix.spinnaker.clouddriver.google.deploy.instancegroups.GoogleServerGroupManagersFactory
import com.netflix.spinnaker.clouddriver.google.deploy.instancegroups.GoogleServerGroupOperationPoller
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
  GoogleServerGroupManagersFactory serverGroupManagersFactory
  GoogleServerGroupManagers serverGroupManagers
  GoogleNamedAccountCredentials credentials
  GoogleServerGroupOperationPoller poller

  def setup() {
    task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)

    poller = Mock(GoogleServerGroupOperationPoller)

    serverGroupManagers = Mock(GoogleServerGroupManagers) {
      _ * getOperationPoller() >> poller
    }

    serverGroupManagersFactory = Mock(GoogleServerGroupManagersFactory) {
      _ * getManagers(*_) >> serverGroupManagers
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
    def operation = new SetStatefulDiskAtomicOperation(clusterProvider, serverGroupManagersFactory, description)
    def updateOp = new Operation(name: "xyzzy", status: "DONE")
    _ * serverGroupManagers.get() >> new InstanceGroupManager()

    when:
    operation.operate([])

    then:
    1 * serverGroupManagers.update({
      it.getStatefulPolicy().getPreservedState().getDisks().containsKey(DEVICE_NAME)
    }) >> updateOp
    1 * poller.waitForOperation(updateOp, /* timeout= */ null, task, /* phase= */ _)
  }
}
