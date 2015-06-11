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
import com.google.api.services.replicapool.model.InstanceGroupManagersRecreateInstancesRequest
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.RecreateGoogleReplicaPoolInstancesDescription
import spock.lang.Specification
import spock.lang.Subject

class RecreateGoogleReplicaPoolInstancesAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-instance1", "my-app7-dev-v000-instance2"]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should recreate instances"() {
    setup:
      def computeMock = Mock(Compute)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def request = new InstanceGroupManagersRecreateInstancesRequest().setInstances(INSTANCE_IDS)

      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersRecreateMock = Mock(Replicapool.InstanceGroupManagers.RecreateInstances)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new RecreateGoogleReplicaPoolInstancesDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                          instanceIds: INSTANCE_IDS,
                                                                          zone: ZONE,
                                                                          accountName: ACCOUNT_NAME,
                                                                          credentials: credentials)
      @Subject def operation = new RecreateGoogleReplicaPoolInstancesAtomicOperation(description, replicaPoolBuilderMock)

    when:
      operation.operate([])

    then:
      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.recreateInstances(PROJECT_NAME,
                                                      ZONE,
                                                      REPLICA_POOL_NAME,
                                                      request) >> instanceGroupManagersRecreateMock
      1 * instanceGroupManagersRecreateMock.execute()
  }
}
