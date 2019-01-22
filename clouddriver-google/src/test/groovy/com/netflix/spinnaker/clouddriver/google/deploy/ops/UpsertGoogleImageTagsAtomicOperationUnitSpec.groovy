/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleImageTagsDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.security.TestDefaults
import com.netflix.spinnaker.clouddriver.googlecommon.batch.GoogleBatchRequest
import groovy.mock.interceptor.MockFor
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleImageTagsAtomicOperationUnitSpec extends Specification implements TestDefaults {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my-project"
  private static final IMAGE_NAME = "debian-7-wheezy-v20140415"
  private static final IMAGE_SELF_LINK = "https://compute.googleapis.com/compute/alpha/projects/$PROJECT_NAME/global/images/spinnaker-rosco-all-20161229193556-precise"
  private static final BASE_IMAGE_PROJECTS = ["centos-cloud", "ubuntu-os-cloud"]
  private static final TAGS = ['some-key-1': 'some-val-2']
  private static final LABELS = ['some-existing-key-1': 'some-existing-val-2']

  def registry = new DefaultRegistry()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should set labels on image with no existing labels"() {
    setup:
      def computeMock = new MockFor(Compute)
      def googleBatchMock = new MockFor(GoogleBatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)

      def imagesMock = Mock(Compute.Images)
      def setLabelsMock = Mock(Compute.Images.SetLabels)
      def globalSetLabelsRequest

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
          items: [new Image(name: IMAGE_NAME, selfLink: IMAGE_SELF_LINK)]
        )
        callback.onSuccess(imageList, null)
      }

      computeMock.demand.images { imagesMock }
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
            def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
            @Subject def operation = new UpsertGoogleImageTagsAtomicOperation(description)
            operation.registry = registry
            operation.googleConfigurationProperties = new GoogleConfigurationProperties(baseImageProjects: BASE_IMAGE_PROJECTS)
            operation.operate([])
          }
        }
      }

    then:
      1 * imagesMock.setLabels(PROJECT_NAME, IMAGE_NAME, { globalSetLabelsRequest = it }) >> setLabelsMock
      1 * setLabelsMock.execute()
      globalSetLabelsRequest.labels == TAGS
  }

  void "should add to labels on image with existing labels"() {
    setup:
      def computeMock = new MockFor(Compute)
      def googleBatchMock = new MockFor(GoogleBatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)

      def imagesMock = Mock(Compute.Images)
      def setLabelsMock = Mock(Compute.Images.SetLabels)
      def globalSetLabelsRequest

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
          items: [new Image(name: IMAGE_NAME, selfLink: IMAGE_SELF_LINK, labels: LABELS)]
        )
        callback.onSuccess(imageList, null)
      }

      computeMock.demand.images { imagesMock }
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
            def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
            @Subject def operation = new UpsertGoogleImageTagsAtomicOperation(description)
            operation.registry = registry
            operation.googleConfigurationProperties = new GoogleConfigurationProperties(baseImageProjects: BASE_IMAGE_PROJECTS)
            operation.operate([])
          }
        }
      }

    then:
      1 * imagesMock.setLabels(PROJECT_NAME, IMAGE_NAME, { globalSetLabelsRequest = it }) >> setLabelsMock
      1 * setLabelsMock.execute()
      globalSetLabelsRequest.labels == LABELS + TAGS
  }

  void "should fail to create instance because image is invalid"() {
    setup:
      def computeMock = new MockFor(Compute)
      def googleBatchMock = new MockFor(GoogleBatchRequest)
      def imageProjects = [PROJECT_NAME] + BASE_IMAGE_PROJECTS
      def listMock = new MockFor(Compute.Images.List)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
        new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
        httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()
      def emptyImageList = new ImageList(
        selfLink: "https://compute.googleapis.com/compute/alpha/projects/$PROJECT_NAME/global/images",
        items: []
      )

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
        callback.onSuccess(emptyImageList, null)
        callback.onSuccess(emptyImageList, null)
        callback.onSuccess(emptyImageList, null)
      }

    when:
      googleBatchMock.use {
        computeMock.use {
          listMock.use {
            def compute = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build()

            def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(compute).build()
            def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME,
                                                                   credentials: credentials)
            @Subject def operation = new UpsertGoogleImageTagsAtomicOperation(description)
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
