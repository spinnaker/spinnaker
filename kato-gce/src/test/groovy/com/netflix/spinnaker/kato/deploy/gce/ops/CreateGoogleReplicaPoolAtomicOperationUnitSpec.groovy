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
import com.google.api.services.replicapool.Replicapool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.gce.description.CreateGoogleReplicaPoolDescription
import com.netflix.spinnaker.kato.deploy.gce.GCEUtil
import com.netflix.spinnaker.kato.deploy.gce.GCEResourceNotFoundException
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials
import spock.lang.Specification
import spock.lang.Subject

class CreateGoogleReplicaPoolAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final INITIAL_NUM_REPLICAS = 3
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final TYPE = "f1-micro"
  private static final ZONE = "us-central1-b"

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should create replica pool"() {
    setup:
      def computeMock = Mock(Compute)
      def imagesMock = Mock(Compute.Images)
      def listMock = Mock(Compute.Images.List)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def poolsMock = Mock(Replicapool.Pools)
      def poolsInsertMock = Mock(Replicapool.Pools.Insert)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleReplicaPoolDescription(application: APPLICATION,
                                                               stack: STACK,
                                                               initialNumReplicas: INITIAL_NUM_REPLICAS,
                                                               image: IMAGE,
                                                               type: TYPE,
                                                               zone: ZONE,
                                                               accountName: ACCOUNT_NAME,
                                                               credentials: credentials)
      @Subject def operation = new CreateGoogleReplicaPoolAtomicOperation(description, replicaPoolBuilderMock)

    when:
      operation.operate([])

    then:
      0 * computeMock._
      1 * computeMock.images() >> imagesMock
      1 * imagesMock.list(PROJECT_NAME) >> listMock
      1 * listMock.execute() >> new ImageList(items: [new Image(name: IMAGE)])

      1 * replicaPoolBuilderMock.buildReplicaPool(_, _) >> replicaPoolMock
      1 * replicaPoolMock.pools() >> poolsMock
      1 * poolsMock.insert(PROJECT_NAME, ZONE, _) >> poolsInsertMock
      1 * poolsInsertMock.execute()
  }

  void "should fail to create replica pool because image is invalid"() {
    setup:
      def computeMock = Mock(Compute)
      def imagesMock = Mock(Compute.Images)
      def listMock = Mock(Compute.Images.List)
      def replicaPoolBuilderMock = Mock(ReplicaPoolBuilder)
      def replicaPoolMock = Mock(Replicapool)
      def poolsMock = Mock(Replicapool.Pools)
      def poolsInsertMock = Mock(Replicapool.Pools.Insert)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def description = new CreateGoogleReplicaPoolDescription(application: APPLICATION,
                                                               stack: STACK,
                                                               initialNumReplicas: INITIAL_NUM_REPLICAS,
                                                               image: IMAGE,
                                                               type: TYPE,
                                                               zone: ZONE,
                                                               accountName: ACCOUNT_NAME,
                                                               credentials: credentials)
      @Subject def operation = new CreateGoogleReplicaPoolAtomicOperation(description, replicaPoolBuilderMock)

    when:
      operation.operate([])

    then:
      0 * computeMock._
      ([PROJECT_NAME] + GCEUtil.baseImageProjects).each {
        1 * computeMock.images() >> imagesMock
        1 * imagesMock.list(it) >> listMock
        1 * listMock.execute() >> new ImageList(items: [])
      }
      thrown GCEResourceNotFoundException
  }
}
