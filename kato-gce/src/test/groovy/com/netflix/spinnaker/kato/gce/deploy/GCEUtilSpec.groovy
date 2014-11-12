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

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEResourceNotFoundException
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.security.GoogleCredentials
import spock.lang.Shared
import spock.lang.Specification

class GCEUtilSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
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
      def computeMock = Mock(Compute)
      def imagesMock = Mock(Compute.Images)
      def listMock = Mock(Compute.Images.List)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)
      def soughtImage = new Image(name: IMAGE_NAME)

    when:
      def sourceImage = GCEUtil.querySourceImage(PROJECT_NAME, IMAGE_NAME, computeMock, taskMock, PHASE)

    then:
      0 * computeMock._
      1 * computeMock.images() >> imagesMock
      1 * imagesMock.list(PROJECT_NAME) >> listMock
      1 * listMock.execute() >> new ImageList(items: [soughtImage])
      sourceImage == soughtImage
  }

  void "query source images should fail"() {
    setup:
      def computeMock = Mock(Compute)
      def imagesMock = Mock(Compute.Images)
      def listMock = Mock(Compute.Images.List)
      def credentials = new GoogleCredentials(PROJECT_NAME, computeMock)

    when:
      def sourceImage = GCEUtil.querySourceImage(PROJECT_NAME, IMAGE_NAME, computeMock, taskMock, PHASE)

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
