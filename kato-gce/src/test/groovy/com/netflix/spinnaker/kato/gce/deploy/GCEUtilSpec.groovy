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

package com.netflix.spinnaker.kato.gce.deploy

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.Operation
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import groovy.mock.interceptor.MockFor
import spock.lang.Shared
import spock.lang.Specification

class GCEUtilSpec extends Specification {
  private static final PROJECT_NAME = "my-project"
  private static final IMAGE_NAME = "some-image-name"
  private static final PHASE = "SOME-PHASE"

  @Shared
  def taskMock

  def setupSpec() {
    this.taskMock = Mock(Task)
    TaskRepository.threadLocalTask.set(taskMock)
  }

  void "query source images should succeed"() {
    setup:
      def computeMock = new MockFor(Compute)
      def batchMock = new MockFor(BatchRequest)
      def imageProjects = [PROJECT_NAME] + GCEUtil.baseImageProjects
      def listMock = new MockFor(Compute.Images.List)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
              new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()

      computeMock.demand.batch { new BatchRequest(httpTransport, httpRequestInitializer) }

      JsonBatchCallback<ImageList> callback = null

      for (def imageProject : imageProjects) {
        computeMock.demand.images { return images }
        listMock.demand.queue { imageListBatch, imageListCallback ->
          callback = imageListCallback
        }
      }

      def soughtImage = new Image(name: IMAGE_NAME)

      batchMock.demand.execute {
        def imageList = new ImageList()
        imageList.setItems([soughtImage])
        callback.onSuccess(imageList, null)
      }

    when:
      def sourceImage = null

      batchMock.use {
        computeMock.use {
          listMock.use {
            def compute = new Compute.Builder(
                    httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build()

            sourceImage = GCEUtil.querySourceImage(PROJECT_NAME, IMAGE_NAME, compute, taskMock, PHASE)
          }
        }
      }

    then:
      sourceImage == soughtImage
  }

  void "query source images should fail"() {
    setup:
      def computeMock = new MockFor(Compute)
      def batchMock = new MockFor(BatchRequest)
      def imageProjects = [PROJECT_NAME] + GCEUtil.baseImageProjects
      def listMock = new MockFor(Compute.Images.List)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
              new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def images = new Compute.Builder(
              httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build().images()

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
        imageList.setItems([new Image(name: IMAGE_NAME + "-WRONG")])
        callback.onSuccess(imageList, null)
      }

    when:
      def sourceImage = null

      batchMock.use {
        computeMock.use {
          listMock.use {
            def compute = new Compute.Builder(
                    httpTransport, jsonFactory, httpRequestInitializer).setApplicationName("test").build()

            sourceImage = GCEUtil.querySourceImage(PROJECT_NAME, IMAGE_NAME, compute, taskMock, PHASE)
          }
        }
      }

    then:
      thrown GCEResourceNotFoundException
  }

  void "waitForOperation should query the operation at least once"() {
    expect:
      GCEOperationUtil.waitForOperation({return new Operation(status: "DONE")}, 0,
          new GCEOperationUtil.Clock()) == new Operation(status: "DONE")
  }

  void "waitForOperation should return null on timeout"() {
    expect:
      GCEOperationUtil.waitForOperation({return new Operation(status: "PENDING")}, 0,
          new GCEOperationUtil.Clock()) == null
  }

  void "waitForOperation should retry until timeout"() {
    setup:
      def getOperationMock = Mock(Closure)
      def clockMock = Mock(GCEOperationUtil.Clock)

    when:
      GCEOperationUtil.waitForOperation(getOperationMock, 5, clockMock)

    then:
      1 * clockMock.currentTimeMillis() >> 0
      1 * getOperationMock.call() >> new Operation(status: "PENDING")

    then:
      1 * clockMock.currentTimeMillis() >> 2500
      1 * getOperationMock.call() >> new Operation(status: "PENDING")

    then:
      1 * clockMock.currentTimeMillis() >> 5000
      0 * getOperationMock.call()
  }

  void "instance metadata with zero key-value pairs roundtrips properly"() {
    setup:
      def instanceMetadata = [:]

    when:
      def computeMetadata = GCEUtil.buildMetadataFromMap(instanceMetadata)
      def roundtrippedMetadata = GCEUtil.buildMapFromMetadata(computeMetadata)

    then:
      roundtrippedMetadata == instanceMetadata
  }

  void "instance metadata with exactly one key-value pair roundtrips properly"() {
    setup:
      def instanceMetadata = [someTestKey: "someTestValue"]

    when:
      def computeMetadata = GCEUtil.buildMetadataFromMap(instanceMetadata)
      def roundtrippedMetadata = GCEUtil.buildMapFromMetadata(computeMetadata)

    then:
      roundtrippedMetadata == instanceMetadata
  }

  void "instance metadata with more than one key-value pair roundtrips properly"() {
    setup:
      def instanceMetadata = [keyA: "valueA", keyB: "valueB", keyC: "valueC"]

    when:
      def computeMetadata = GCEUtil.buildMetadataFromMap(instanceMetadata)
      def roundtrippedMetadata = GCEUtil.buildMapFromMetadata(computeMetadata)

    then:
      roundtrippedMetadata == instanceMetadata
  }

  void "can derive network load balancer names from target pool urls"() {
    setup:
      def targetPoolUrls = ["https://www.googleapis.com/compute/v1/projects/shared-spinnaker/regions/us-central1/targetPools/testlb1-target-pool-1417811497341",
                            "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/regions/us-central1/targetPools/testlb2-target-pool-1417811497567"]

    when:
      def networkLoadBalancerNames = GCEUtil.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(targetPoolUrls)

    then:
      networkLoadBalancerNames == ["testlb1", "testlb2"]
  }

  void "can derive network load balancer names from empty target pool urls"() {
    setup:
      def targetPoolUrls = []

    when:
      def networkLoadBalancerNames = GCEUtil.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(targetPoolUrls)

    then:
      networkLoadBalancerNames == []
  }

  void "can derive network load balancer names from null target pool urls"() {
    setup:
      def targetPoolUrls = null

    when:
      def networkLoadBalancerNames = GCEUtil.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(targetPoolUrls)

    then:
      networkLoadBalancerNames == []
  }
}
