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
import com.google.api.services.compute.model.*
import com.google.api.services.replicapool.Replicapool
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.google.api.services.replicapool.model.InstanceGroupManagersSetTargetPoolsRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class DisableGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final REPLICA_POOL_NAME = "mjdapp-dev-v009"
  private static final TARGET_POOL_NAME_1 = "testlb-target-pool-1417967954401";
  private static final TARGET_POOL_NAME_2 = "testlb2-target-pool-1417963107058";
  private static final TARGET_POOL_URL_1 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/regions/us-central1/targetPools/$TARGET_POOL_NAME_1"
  private static final TARGET_POOL_URL_2 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/regions/us-central1/targetPools/$TARGET_POOL_NAME_2"
  private static final TARGET_POOL_URLS = [TARGET_POOL_URL_1, TARGET_POOL_URL_2]
  private static final INSTANCE_URL_1 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-a/instances/mjdapp-dev-v009-hnyp"
  private static final INSTANCE_URL_2 =
          "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-a/instances/mjdapp-dev-v009-qtow"
  private static final ZONE = "us-central1-b"
  private static final REGION = "us-central1"

  def computeMock
  def replicaPoolBuilderMock
  def replicaPoolMock
  def zonesMock
  def zonesGetMock
  def instanceGroupManagersMock
  def instanceGroupManagersGetMock
  def targetPoolsMock
  def targetPoolsGetMock
  def targetPoolsRemoveInstance
  def targetPool
  def instanceGroupManagersSetTargetPoolsMock

  def zone
  def instanceGroupManager
  def items
  def credentials
  def description

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    computeMock = Mock(Compute)
    credentials = new GoogleCredentials(PROJECT_NAME, computeMock)

    replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
    replicaPoolMock = Mock(Replicapool)

    zonesMock = Mock(Compute.Zones)
    zonesGetMock = Mock(Compute.Zones.Get)
    zone = new Zone(region: REGION)

    instanceGroupManagersMock = Mock(Replicapool.InstanceGroupManagers)
    instanceGroupManagersGetMock = Mock(Replicapool.InstanceGroupManagers.Get)
    instanceGroupManager = new InstanceGroupManager(targetPools: TARGET_POOL_URLS)

    targetPoolsMock = Mock(Compute.TargetPools)
    targetPoolsGetMock = Mock(Compute.TargetPools.Get)
    targetPoolsRemoveInstance = Mock(Compute.TargetPools.RemoveInstance)
    targetPool = new TargetPool(instances: [INSTANCE_URL_1, INSTANCE_URL_2])

    instanceGroupManagersSetTargetPoolsMock = Mock(Replicapool.InstanceGroupManagers.SetTargetPools)

    description = new EnableDisableGoogleServerGroupDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                                zone: ZONE,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
  }

  void "should remove instances and detach load balancers"() {
    setup:
      @Subject def operation =
              new DisableGoogleServerGroupAtomicOperation(description, replicaPoolBuilderMock, null)

    when:
      operation.operate([])

    then:
      1 * computeMock.zones() >> zonesMock
      1 * zonesMock.get(PROJECT_NAME, ZONE) >> zonesGetMock
      1 * zonesGetMock.execute() >> zone

      2 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.get(PROJECT_NAME, ZONE, REPLICA_POOL_NAME) >> instanceGroupManagersGetMock
      1 * instanceGroupManagersGetMock.execute() >> instanceGroupManager

      [TARGET_POOL_NAME_1, TARGET_POOL_NAME_2].each { targetPoolLocalName ->
        1 * computeMock.targetPools() >> targetPoolsMock
        1 * targetPoolsMock.get(PROJECT_NAME, REGION, targetPoolLocalName) >> targetPoolsGetMock
        1 * targetPoolsGetMock.execute() >> targetPool
      }

      [TARGET_POOL_NAME_1, TARGET_POOL_NAME_2].each { targetPoolLocalName ->
        1 * computeMock.targetPools() >> targetPoolsMock
        1 * targetPoolsMock.removeInstance(PROJECT_NAME, REGION, targetPoolLocalName, _) >> targetPoolsRemoveInstance
        1 * targetPoolsRemoveInstance.execute()
      }

      1 * replicaPoolMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.setTargetPools(
              PROJECT_NAME,
              ZONE,
              REPLICA_POOL_NAME,
              new InstanceGroupManagersSetTargetPoolsRequest(targetPools: [])) >> instanceGroupManagersSetTargetPoolsMock
      1 * instanceGroupManagersSetTargetPoolsMock.execute()
  }
}
