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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManagersRecreateInstancesRequest
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.GoogleApiTestUtils

import spock.lang.Specification
import spock.lang.Subject

class TerminateGoogleInstancesAtomicOperationUnitSpec extends Specification {
  private static final MANAGED_INSTANCE_GROUP_NAME = "my-app7-dev-v000"
  private static final MANAGED_INSTANCE_GROUP_SELF_LINK =
    "https://compute.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instanceGroupManagers/$MANAGED_INSTANCE_GROUP_NAME"
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final ID_GOOD_PREFIX = "my-app7-dev-v000-good";
  private static final ID_BAD_PREFIX = "my-app7-dev-v000-bad";
  private static final GOOD_INSTANCE_IDS = ["${ID_GOOD_PREFIX}1".toString(), "${ID_GOOD_PREFIX}2".toString()]
  private static final BAD_INSTANCE_IDS = ["${ID_BAD_PREFIX}1", "${ID_BAD_PREFIX}2"]
  private static final ALL_INSTANCE_IDS = ["${ID_GOOD_PREFIX}1", "${ID_BAD_PREFIX}1",
                                           "${ID_GOOD_PREFIX}2", "${ID_BAD_PREFIX}2"]
  private static final GOOD_INSTANCE_URLS = [
    "https://compute.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/${ID_GOOD_PREFIX}1",
    "https://compute.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/${ID_GOOD_PREFIX}2"
  ]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should terminate instances without managed instance group"() {
    setup:
      def registry = new DefaultRegistry()
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def deleteMock = Mock(Compute.Instances.Delete)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new TerminateGoogleInstancesDescription(zone: ZONE,
                                                                instanceIds: GOOD_INSTANCE_IDS,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)
      operation.registry = registry

    when:
      operation.operate([])

    then:
      0 * computeMock._
      GOOD_INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.delete(PROJECT_NAME, ZONE, it) >> deleteMock
        1 * deleteMock.execute()
      }
  }

  void "should terminate all known instances and fail on all unknown instances without managed instance group"() {
    setup:
      def registry = new DefaultRegistry()
      def computeMock = Mock(Compute)
      def notFoundException = GoogleApiTestUtils.makeHttpResponseException(404)
      def instancesMock = Mock(Compute.Instances)
      def deleteMock = Mock(Compute.Instances.Delete)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new TerminateGoogleInstancesDescription(zone: ZONE,
                                                                instanceIds: ALL_INSTANCE_IDS,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)
      operation.registry = registry

    when:
      operation.operate([])

    then:
      0 * computeMock._
      GOOD_INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.delete(PROJECT_NAME, ZONE, it) >> deleteMock
        1 * deleteMock.execute()
      }
      BAD_INSTANCE_IDS.each {
        1 * computeMock.instances() >> instancesMock
        1 * instancesMock.delete(PROJECT_NAME, ZONE, it) >> deleteMock
        1 * deleteMock.execute() >> { throw notFoundException }
      }
      registry.timer(
          GoogleApiTestUtils.makeOkId(
            registry, "compute.instances.delete",
            [scope: "zonal", zone: ZONE])
      ).count() == GOOD_INSTANCE_IDS.size
      registry.timer(
          GoogleApiTestUtils.makeId(
            registry, "compute.instances.delete", 404,
            [scope: "zonal", zone: ZONE])
      ).count() == BAD_INSTANCE_IDS.size
      thrown IOException
  }

  void "should recreate instances with managed instance group"() {
    setup:
      def registry = new DefaultRegistry()
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(
        regional: isRegional,
        zone: ZONE,
        instances: GOOD_INSTANCE_URLS.collect {
          new GoogleInstance(
            name: GCEUtil.getLocalName(it),
            selfLink: it)
        }
      ).view
      def computeMock = Mock(Compute)
      def request = new InstanceGroupManagersRecreateInstancesRequest().setInstances(GOOD_INSTANCE_URLS)
      def regionInstanceGroupManagersMock = Mock(Compute.RegionInstanceGroupManagers)
      def regionInstanceGroupManagersRecreateInstancesMock = Mock(Compute.RegionInstanceGroupManagers.RecreateInstances)
      def regionalTimerId = GoogleApiTestUtils.makeOkId(
            registry, "compute.regionInstanceGroupManagers.recreateInstances",
            [scope: "regional", region: REGION])

      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def instanceGroupManagersRecreateInstancesMock = Mock(Compute.InstanceGroupManagers.RecreateInstances)
      def zonalTimerId = GoogleApiTestUtils.makeOkId(
            registry, "compute.instanceGroupManagers.recreateInstances",
            [scope: "zonal", zone: ZONE])

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new TerminateGoogleInstancesDescription(serverGroupName: MANAGED_INSTANCE_GROUP_NAME,
                                                                instanceIds: GOOD_INSTANCE_IDS,
                                                                region: REGION,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)
      operation.registry = registry
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, MANAGED_INSTANCE_GROUP_NAME) >> serverGroup

      if (isRegional) {
        1 * computeMock.regionInstanceGroupManagers() >> regionInstanceGroupManagersMock
        1 * regionInstanceGroupManagersMock.recreateInstances(PROJECT_NAME,
                                                              location,
                                                              MANAGED_INSTANCE_GROUP_NAME,
                                                              request) >> regionInstanceGroupManagersRecreateInstancesMock
        1 * regionInstanceGroupManagersRecreateInstancesMock.execute()
      } else {
        1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
        1 * instanceGroupManagersMock.recreateInstances(PROJECT_NAME,
                                                        location,
                                                        MANAGED_INSTANCE_GROUP_NAME,
                                                        request) >> instanceGroupManagersRecreateInstancesMock
        1 * instanceGroupManagersRecreateInstancesMock.execute()
      }
      registry.timer(regionalTimerId).count() == (isRegional ? 1 : 0)
      registry.timer(zonalTimerId).count() == (isRegional ? 0 : 1)

    where:
      isRegional | location
      false      | ZONE
      true       | REGION
  }
}
