/*
 * Copyright 2015 Google, Inc.
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


package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.replicapool.Replicapool
import com.google.api.services.replicapool.model.InstanceGroupManagersDeleteInstancesRequest
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.TerminateAndDecrementGoogleServerGroupDescription
import spock.lang.Specification
import spock.lang.Subject

class TerminateAndDecrementGoogleReplicaPoolAtomicOperationUnitSpec extends Specification {
  private static final REPLICA_POOL_NAME = "my-replica-pool";
  private static final ZONE = "my-zone"
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-1", "my-app7-dev-v000-2"];

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should terminate instances"() {
    setup:
      def computeMock = Mock(Compute)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def request = new InstanceGroupManagersDeleteInstancesRequest().setInstances(INSTANCE_IDS)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersDeleteInstancesMock = Mock(Replicapool.InstanceGroupManagers.DeleteInstances)

      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new TerminateAndDecrementGoogleServerGroupDescription(
          replicaPoolName: REPLICA_POOL_NAME,
          zone: ZONE,
          instanceIds: INSTANCE_IDS,
          accountName: ACCOUNT_NAME,
          credentials: credentials)
      @Subject def operation = new TerminateAndDecrementGoogleServerGroupAtomicOperation(description, replicaPoolBuilderMock)

    when:
      operation.operate([])

    then:
      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.deleteInstances(PROJECT_NAME,
                                                    ZONE,
                                                    REPLICA_POOL_NAME,
                                                    request) >> instanceGroupManagersDeleteInstancesMock
      1 * instanceGroupManagersDeleteInstancesMock.execute()
  }
}
