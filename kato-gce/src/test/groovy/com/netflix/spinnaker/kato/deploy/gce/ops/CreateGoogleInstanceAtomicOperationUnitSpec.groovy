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
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.InstanceList
import com.google.api.services.compute.model.MachineType
import com.google.api.services.compute.model.MachineTypeList
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.NetworkList
import com.google.api.services.replicapool.Replicapool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.gce.GCEResourceNotFoundException
import com.netflix.spinnaker.kato.deploy.gce.GCEUtil
import com.netflix.spinnaker.kato.deploy.gce.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class CreateGoogleInstanceAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final INSTANCE_NAME = "my-app-v000"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final NETWORK_NAME = "default"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final MACHINE_TYPE_LINK = "http://..."
  private static final ZONE = "us-central1-b"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create instance"() {
    setup:
      def computeMock = Mock(Compute)
      def machineTypesMock = Mock(Compute.MachineTypes)
      def machineTypesListMock = Mock(Compute.MachineTypes.List)
      def imagesMock = Mock(Compute.Images)
      def imagesListMock = Mock(Compute.Images.List)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def instancesMock = Mock(Compute.Instances)
      def instancesInsertMock = Mock(Compute.Instances.Insert)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                            image: IMAGE,
                                                            instanceType: INSTANCE_TYPE,
                                                            zone: ZONE,
                                                            accountName: ACCOUNT_NAME,
                                                            credentials: credentials)
      @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.machineTypes() >> machineTypesMock
      1 * machineTypesMock.list(PROJECT_NAME, ZONE) >> machineTypesListMock
      1 * machineTypesListMock.execute() >> new MachineTypeList(items: [new MachineType(name: INSTANCE_TYPE,
                                                                                        selfLink: MACHINE_TYPE_LINK)])
      1 * computeMock.images() >> imagesMock
      1 * imagesMock.list(PROJECT_NAME) >> imagesListMock
      1 * imagesListMock.execute() >> new ImageList(items: [new Image(name: IMAGE)])
      1 * computeMock.networks() >> networksMock
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.insert(PROJECT_NAME, ZONE, _) >> instancesInsertMock
      1 * instancesInsertMock.execute()
  }

  void "should fail to create instance because machine type is invalid"() {
    setup:
    def computeMock = Mock(Compute)
    def machineTypesMock = Mock(Compute.MachineTypes)
    def machineTypesListMock = Mock(Compute.MachineTypes.List)
    def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
    def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                          image: IMAGE,
                                                          instanceType: INSTANCE_TYPE,
                                                          zone: ZONE,
                                                          accountName: ACCOUNT_NAME,
                                                          credentials: credentials)
    @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * computeMock.machineTypes() >> machineTypesMock
    1 * machineTypesMock.list(PROJECT_NAME, ZONE) >> machineTypesListMock
    1 * machineTypesListMock.execute() >> new MachineTypeList(items: [])
    thrown GCEResourceNotFoundException
  }

  void "should fail to create instance because image is invalid"() {
    setup:
      def computeMock = Mock(Compute)
      def machineTypesMock = Mock(Compute.MachineTypes)
      def machineTypesListMock = Mock(Compute.MachineTypes.List)
      def imagesMock = Mock(Compute.Images)
      def imagesListMock = Mock(Compute.Images.List)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                            image: IMAGE,
                                                            instanceType: INSTANCE_TYPE,
                                                            zone: ZONE,
                                                            accountName: ACCOUNT_NAME,
                                                            credentials: credentials)
      @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)

    when:
      operation.operate([])

    then:
      1 * computeMock.machineTypes() >> machineTypesMock
      1 * machineTypesMock.list(PROJECT_NAME, ZONE) >> machineTypesListMock
      1 * machineTypesListMock.execute() >> new MachineTypeList(items: [new MachineType(name: INSTANCE_TYPE,
                                                                                        selfLink: MACHINE_TYPE_LINK)])
      ([PROJECT_NAME] + GCEUtil.baseImageProjects).each {
        1 * computeMock.images() >> imagesMock
        1 * imagesMock.list(it) >> imagesListMock
        1 * imagesListMock.execute() >> new ImageList(items: [])
      }
      thrown GCEResourceNotFoundException
  }
}
