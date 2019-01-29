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
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.GoogleApiTestUtils
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.security.TestDefaults
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.config.GoogleConfiguration
import groovy.mock.interceptor.MockFor
import spock.lang.Specification
import spock.lang.Subject

class CreateGoogleInstanceAtomicOperationUnitSpec extends Specification implements TestDefaults {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my-project"
  private static final INSTANCE_NAME = "my-app-v000"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final ZONE = "us-central1-b"
  private static final BASE_IMAGE_PROJECTS = ["centos-cloud", "ubuntu-os-cloud"]

  def registry = new DefaultRegistry()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create instance"() {
    setup:
      def computeMock = new MockFor(Compute)
      def googleBatchMock = new MockFor(GoogleBatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)

      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def instancesMock = Mock(Compute.Instances)
      def instancesInsertMock = Mock(Compute.Instances.Insert)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
              new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()

      JsonBatchCallback<ImageList> callback = null

      for (def imageProject : imageProjects) {
        computeMock.demand.images { return images }
        listMock.demand.setFilter { }
        googleBatchMock.demand.queue { imageList, imageListCallback ->
          callback = imageListCallback
        }
      }

      googleBatchMock.demand.size { return 1 }

      googleBatchMock.demand.execute {
        def imageList = new ImageList(
          selfLink: "https://compute.googleapis.com/compute/alpha/projects/$PROJECT_NAME/global/images",
          items: [new Image(name: IMAGE)]
        )
        callback.onSuccess(imageList, null)
      }

      computeMock.demand.instances { instancesMock }

      computeMock.ignore('asBoolean')

    when:
      googleBatchMock.use {
        computeMock.use {
          listMock.use {
            def compute = new Compute.Builder(
                    httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build()

            def credentials = new GoogleNamedAccountCredentials.Builder()
                    .project(PROJECT_NAME)
                    .compute(compute)
                    .regionToZonesMap(REGION_TO_ZONES)
                    .locationToInstanceTypesMap(LOCATION_TO_INSTANCE_TYPES)
                    .build()
            def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                                  image: IMAGE,
                                                                  instanceType: INSTANCE_TYPE,
                                                                  zone: ZONE,
                                                                  accountName: ACCOUNT_NAME,
                                                                  credentials: credentials)
            @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)
            operation.registry = registry
            operation.googleConfigurationProperties = new GoogleConfigurationProperties(baseImageProjects: BASE_IMAGE_PROJECTS)
            operation.googleDeployDefaults = new GoogleConfiguration.DeployDefaults(fallbackInstanceTypeDisks: [new GoogleDisk(type: 'pd-ssd', sizeGb: 10)])
            operation.googleNetworkProvider = googleNetworkProviderMock
            operation.operate([])
          }
        }
      }

    then:
      1 * googleNetworkProviderMock.getAllMatchingKeyPattern("gce:networks:default:$ACCOUNT_NAME:global") >> [new GoogleNetwork()]
      1 * instancesMock.insert(PROJECT_NAME, ZONE, _) >> instancesInsertMock
      1 * instancesInsertMock.execute()
      registry.timer(
          GoogleApiTestUtils.makeOkId(
            registry, "compute.instances.insert",
            [scope: "zonal", zone: ZONE])
      ).count() == 1
  }

  void "should fail to create instance because machine type is invalid"() {
    setup:
      def computeMock = Mock(Compute)
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                            image: IMAGE,
                                                            instanceType: INSTANCE_TYPE,
                                                            zone: ZONE,
                                                            accountName: ACCOUNT_NAME,
                                                            credentials: credentials)
      @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)
      operation.registry = registry

    when:
      operation.operate([])

    then:
      thrown GoogleResourceNotFoundException
  }

  void "should fail to create instance because image is invalid"() {
    setup:
      def computeMock = new MockFor(Compute)
      def batchMock = new MockFor(BatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
              new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()

      computeMock.demand.batch { new BatchRequest(httpTransport, httpRequestInitializer) }
      computeMock.ignore('asBoolean')

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

            def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(compute).build()
            def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                                  image: IMAGE,
                                                                  instanceType: INSTANCE_TYPE,
                                                                  zone: ZONE,
                                                                  accountName: ACCOUNT_NAME,
                                                                  credentials: credentials)
            @Subject def operation = new CreateGoogleInstanceAtomicOperation(description)
            operation.registry = registry
            operation.googleConfigurationProperties = new GoogleConfigurationProperties(baseImageProjects: BASE_IMAGE_PROJECTS)
            operation.operate([])
          }
        }
      }

    then:
      thrown GoogleResourceNotFoundException
  }
}
