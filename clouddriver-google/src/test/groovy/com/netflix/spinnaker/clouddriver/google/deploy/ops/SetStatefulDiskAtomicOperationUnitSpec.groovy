/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.InstanceGroupManager
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.compute.FakeGoogleComputeOperationRequest
import com.netflix.spinnaker.clouddriver.google.compute.FakeGoogleComputeRequest
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory
import com.netflix.spinnaker.clouddriver.google.compute.GoogleServerGroupManagers
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription
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
    def updateOp = new FakeGoogleComputeOperationRequest<>()
    def getManagerRequest = FakeGoogleComputeRequest.createWithResponse(new InstanceGroupManager())
    _ * serverGroupManagers.get() >> getManagerRequest

    when:
    operation.operate([])

    then:
    1 * serverGroupManagers.update({
      it.getStatefulPolicy().getPreservedState().getDisks().containsKey(DEVICE_NAME)
    }) >> updateOp

    assert updateOp.waitedForCompletion()
  }
}
