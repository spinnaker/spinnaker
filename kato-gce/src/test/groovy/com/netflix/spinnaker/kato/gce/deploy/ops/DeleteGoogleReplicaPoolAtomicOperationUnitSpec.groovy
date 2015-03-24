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
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleReplicaPoolDescription
import spock.lang.Specification
import spock.lang.Subject

class DeleteGoogleReplicaPoolAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final INSTANCE_TEMPLATE_NAME = "$REPLICA_POOL_NAME-${System.currentTimeMillis()}"
  private static final INSTANCE_GROUP_OP_NAME = "spinnaker-test-v000-op"
  private static final ZONE = "us-central1-b"
  private static final DONE = "DONE"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should delete replica pool"() {
    setup:
      def computeMock = Mock(Compute)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
      def instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
      def zoneOperations = Mock(Replicapool.ZoneOperations)
      def zoneOperationsGet = Mock(Replicapool.ZoneOperations.Get)
      def instanceGroupManager = new InstanceGroupManager()
      instanceGroupManager.setName(REPLICA_POOL_NAME)
      instanceGroupManager.setInstanceTemplate(INSTANCE_TEMPLATE_NAME)
      def instanceGroupManagersDeleteMock = Mock(Replicapool.InstanceGroupManagers.Delete)
      def instanceGroupManagersDeleteOp = new com.google.api.services.replicapool.model.Operation(
          name: INSTANCE_GROUP_OP_NAME,
          status: DONE)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
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
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.delete(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersDeleteMock
      1 * instanceGroupManagersDeleteMock.execute() >> instanceGroupManagersDeleteOp

      1 * replicaPoolMock.zoneOperations() >> zoneOperations
      1 * zoneOperations.get(PROJECT_NAME, ZONE, INSTANCE_GROUP_OP_NAME) >> zoneOperationsGet
      1 * zoneOperationsGet.execute() >> instanceGroupManagersDeleteOp

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.delete(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()
  }
}
