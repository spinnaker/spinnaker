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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.MachineType
import com.google.api.services.compute.model.MachineTypeList
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.NetworkList
import com.google.api.services.compute.model.Zone
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import groovy.mock.interceptor.MockFor
import spock.lang.Specification
import spock.lang.Subject

class CreateGoogleInstanceAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my-project"
  private static final INSTANCE_NAME = "my-app-v000"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final NETWORK_NAME = "default"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final MACHINE_TYPE_LINK = "http://..."
  private static final ZONE = "us-central1-b"
  private static final BASE_IMAGE_PROJECTS = ["centos-cloud", "ubuntu-os-cloud"]
  private static final REGION = "us-central1"
  private static final REGION_URL = "https://www.googleapis.com/compute/v1/projects/$PROJECT_NAME/regions/$REGION"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create instance"() {
    setup:
      def computeMock = new MockFor(Compute)
      def batchMock = new MockFor(BatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)

      def zonesMock = Mock(Compute.Zones)
      def zonesGetMock = Mock(Compute.Zones.Get)
      def zonesGetReal = new Zone(region: [REGION_URL])

      def machineTypesMock = Mock(Compute.MachineTypes)
      def machineTypesListMock = Mock(Compute.MachineTypes.List)
      def networksMock = Mock(Compute.Networks)
      def networksListMock = Mock(Compute.Networks.List)
      def instancesMock = Mock(Compute.Instances)
      def instancesInsertMock = Mock(Compute.Instances.Insert)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
              new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()

      computeMock.demand.zones { zonesMock }
      computeMock.demand.machineTypes { machineTypesMock }
      computeMock.demand.batch { new BatchRequest(httpTransport, httpRequestInitializer) }

      JsonBatchCallback<ImageList> callback = null

      for (def imageProject : imageProjects) {
        computeMock.demand.images { return images }
        listMock.demand.queue { imageListBatch, imageListCallback ->
          callback = imageListCallback
        }
      }

      batchMock.demand.execute {
        def imageList = new ImageList()
        imageList.setItems([new Image(name: IMAGE)])
        callback.onSuccess(imageList, null)
      }

      computeMock.demand.networks { networksMock }
      computeMock.demand.instances { instancesMock }

    when:
      batchMock.use {
        computeMock.use {
          listMock.use {
            def compute = new Compute.Builder(
                    httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build()

            def credentials = new GoogleCredentials(PROJECT_NAME, compute)
            def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                                  image: IMAGE,
                                                                  instanceType: INSTANCE_TYPE,
                                                                  zone: ZONE,
                                                                  accountName: ACCOUNT_NAME,
                                                                  credentials: credentials)
            @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)
            operation.googleConfigurationProperties = new GoogleConfigurationProperties(baseImageProjects: BASE_IMAGE_PROJECTS)
            operation.googleDeployDefaults = new GoogleConfiguration.DeployDefaults()
            operation.operate([])
          }
        }
      }

    then:
      1 * zonesMock.get(PROJECT_NAME, ZONE) >> zonesGetMock
      1 * zonesGetMock.execute() >> zonesGetReal

    then:
      1 * machineTypesMock.list(PROJECT_NAME, ZONE) >> machineTypesListMock
      1 * machineTypesListMock.execute() >> new MachineTypeList(items: [new MachineType(name: INSTANCE_TYPE,
                                                                                        selfLink: MACHINE_TYPE_LINK)])
      1 * networksMock.list(PROJECT_NAME) >> networksListMock
      1 * networksListMock.execute() >> new NetworkList(items: [new Network(name: NETWORK_NAME)])
      1 * instancesMock.insert(PROJECT_NAME, ZONE, _) >> instancesInsertMock
      1 * instancesInsertMock.execute()
  }

  void "should fail to create instance because machine type is invalid"() {
    setup:
      def computeMock = Mock(Compute)
      def zonesMock = Mock(Compute.Zones)
      def zonesGetMock = Mock(Compute.Zones.Get)
      def zonesGetReal = new Zone(region: [REGION_URL])
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
      1 * computeMock.zones() >> zonesMock
      1 * zonesMock.get(PROJECT_NAME, ZONE) >> zonesGetMock
      1 * zonesGetMock.execute() >> zonesGetReal

    then:
      1 * computeMock.machineTypes() >> machineTypesMock
      1 * machineTypesMock.list(PROJECT_NAME, ZONE) >> machineTypesListMock
      1 * machineTypesListMock.execute() >> new MachineTypeList(items: [])
      thrown GoogleResourceNotFoundException
  }

  void "should fail to create instance because image is invalid"() {
    setup:
      def computeMock = new MockFor(Compute)
      def batchMock = new MockFor(BatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)
      def zonesMock = Mock(Compute.Zones)
      def zonesGetMock = Mock(Compute.Zones.Get)
      def zonesGetReal = new Zone(region: [REGION_URL])
      def machineTypesMock = Mock(Compute.MachineTypes)
      def machineTypesListMock = Mock(Compute.MachineTypes.List)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
              new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()

      computeMock.demand.zones { zonesMock }
      computeMock.demand.machineTypes { machineTypesMock }
      computeMock.demand.batch { new BatchRequest(httpTransport, httpRequestInitializer) }

      JsonBatchCallback<ImageList> callback = null

      for (def imageProject : imageProjects) {
        computeMock.demand.images { return images }
        listMock.demand.queue { imageListBatch, imageListCallback ->
          callback = imageListCallback
        }
      }

      batchMock.demand.execute {
        def imageList = new ImageList()
        imageList.setItems([new Image(name: IMAGE + "-WRONG")])
        callback.onSuccess(imageList, null)
      }

    when:
      batchMock.use {
        computeMock.use {
          listMock.use {
            def compute = new Compute.Builder(
                    httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build()

            def credentials = new GoogleCredentials(PROJECT_NAME, compute)
            def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                                  image: IMAGE,
                                                                  instanceType: INSTANCE_TYPE,
                                                                  zone: ZONE,
                                                                  accountName: ACCOUNT_NAME,
                                                                  credentials: credentials)
            @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)
            operation.googleConfigurationProperties = new GoogleConfigurationProperties(baseImageProjects: BASE_IMAGE_PROJECTS)
            operation.operate([])
          }
        }
      }

    then:
      1 * zonesMock.get(PROJECT_NAME, ZONE) >> zonesGetMock
      1 * zonesGetMock.execute() >> zonesGetReal

    then:
      1 * machineTypesMock.list(PROJECT_NAME, ZONE) >> machineTypesListMock
      1 * machineTypesListMock.execute() >> new MachineTypeList(items: [new MachineType(name: INSTANCE_TYPE,
                                                                                        selfLink: MACHINE_TYPE_LINK)])
      thrown GoogleResourceNotFoundException
  }
}
