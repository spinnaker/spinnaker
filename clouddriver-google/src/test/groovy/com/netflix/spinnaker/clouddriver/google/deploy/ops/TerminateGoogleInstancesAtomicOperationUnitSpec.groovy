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
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class TerminateGoogleInstancesAtomicOperationUnitSpec extends Specification {
  private static final MANAGED_INSTANCE_GROUP_NAME = "my-app7-dev-v000"
  private static final MANAGED_INSTANCE_GROUP_SELF_LINK =
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instanceGroupManagers/$MANAGED_INSTANCE_GROUP_NAME"
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
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/${ID_GOOD_PREFIX}1",
    "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/${ID_GOOD_PREFIX}2"
  ]

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should terminate instances without managed instance group"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def deleteMock = Mock(Compute.Instances.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new TerminateGoogleInstancesDescription(zone: ZONE,
                                                                instanceIds: GOOD_INSTANCE_IDS,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)

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
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def deleteMock = Mock(Compute.Instances.Delete)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new TerminateGoogleInstancesDescription(zone: ZONE,
                                                                instanceIds: ALL_INSTANCE_IDS,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)

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
        1 * deleteMock.execute() >> { throw new IOException() }
      }
      thrown IOException
  }

  void "should recreate instances with managed instance group"() {
    setup:
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup = new GoogleServerGroup(
        zone: ZONE,
        instances: GOOD_INSTANCE_URLS.collect {
          new GoogleInstance(
            name: GCEUtil.getLocalName(it),
            selfLink: it)
        }
      ).view
      def computeMock = Mock(Compute)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def request = new InstanceGroupManagersRecreateInstancesRequest().setInstances(GOOD_INSTANCE_URLS)

      def instanceGroupManagersRecreateMock = Mock(Compute.InstanceGroupManagers.RecreateInstances)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new TerminateGoogleInstancesDescription(serverGroupName: MANAGED_INSTANCE_GROUP_NAME,
                                                                instanceIds: GOOD_INSTANCE_IDS,
                                                                region: REGION,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new TerminateGoogleInstancesAtomicOperation(description)
      operation.googleClusterProvider = googleClusterProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, MANAGED_INSTANCE_GROUP_NAME) >> serverGroup
      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.recreateInstances(PROJECT_NAME,
                                                      ZONE,
                                                      MANAGED_INSTANCE_GROUP_NAME,
                                                      request) >> instanceGroupManagersRecreateMock
      1 * instanceGroupManagersRecreateMock.execute()
  }
}
