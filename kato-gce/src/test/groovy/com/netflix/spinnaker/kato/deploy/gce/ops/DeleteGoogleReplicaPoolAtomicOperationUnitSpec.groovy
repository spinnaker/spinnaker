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

package com.netflix.spinnaker.kato.deploy.gce.ops

import com.google.api.services.compute.Compute
import com.google.api.services.replicapool.Replicapool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.gce.description.DeleteGoogleReplicaPoolDescription
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleReplicaPoolAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete replica pool"() {
    setup:
      def computeMock = Mock(Compute)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def poolsMock = Mock(Replicapool.Pools)
      def poolsDeleteMock = Mock(Replicapool.Pools.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new DeleteGoogleReplicaPoolDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                               zone: ZONE,
                                                               accountName: ACCOUNT_NAME,
                                                               credentials: credentials)
      @Subject def operation = new DeleteGoogleReplicaPoolAtomicOperation(description, replicaPoolBuilderMock)

    when:
      operation.operate([])

    then:
      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.pools() >> poolsMock
      1 * poolsMock.delete(PROJECT_NAME, ZONE, _, _) >> poolsDeleteMock
      1 * poolsDeleteMock.execute()
  }
}
