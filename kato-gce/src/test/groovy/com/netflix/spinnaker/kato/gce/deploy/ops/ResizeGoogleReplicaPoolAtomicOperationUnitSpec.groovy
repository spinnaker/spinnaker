/*
 * Copyright 2014 Google, Inc.
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
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.ResizeGoogleReplicaPoolDescription
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class ResizeGoogleReplicaPoolAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final DESIRED_NUM_REPLICAS = 5
  private static final ZONE = "us-central1-b"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should resize replica pool"() {
    setup:
      def computeMock = Mock(Compute)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersResizeMock = Mock(Replicapool.InstanceGroupManagers.Resize)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new ResizeGoogleReplicaPoolDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                               numReplicas: DESIRED_NUM_REPLICAS,
                                                               zone: ZONE,
                                                               accountName: ACCOUNT_NAME,
                                                               credentials: credentials)
      @Subject def operation = new ResizeGoogleReplicaPoolAtomicOperation(description, replicaPoolBuilderMock)

    when:
      operation.operate([])

    then:
      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.resize(PROJECT_NAME,
                                           ZONE,
                                           REPLICA_POOL_NAME,
                                           DESIRED_NUM_REPLICAS) >> instanceGroupManagersResizeMock
      1 * instanceGroupManagersResizeMock.execute()
  }
}
