/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.asgard.kato.deploy.gce.handlers

import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.*
import com.netflix.asgard.kato.data.task.Task
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.deploy.gce.description.BasicGoogleDeployDescription
import com.netflix.asgard.kato.security.gce.GoogleCredentials
import spock.lang.Ignore
import spock.lang.Specification

class BasicGoogleDeployHandlerSpec extends Specification {

  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  /**
   * TODO: this is a really hard thing to test.
   */
  @Ignore
  void "handler deploys with netflix specific naming convention"() {
    setup:
    def compute = Mock(Compute)
    def instanceMock = getComputeMock(Compute.Instances, Compute.Instances.List, InstanceList, Instance, null)
    def credentials = new GoogleCredentials("project", compute)
    def description = new BasicGoogleDeployDescription(application: "app", stack: "stack", image: "image", type: "f1-micro", zone: "us-central1-b", credentials: credentials)
    def handler = new BasicGoogleDeployHandler()

    when:
    handler.handle(description, [])

    then:
    10 * compute.machineTypes() >> getComputeMock(Compute.MachineTypes, Compute.MachineTypes.List, MachineTypeList, MachineType, description.type)
    10 * compute.images() >> getComputeMock(Compute.Images, Compute.Images.List, ImageList, Image, description.image)
    10 * compute.networks() >> getComputeMock(Compute.Networks, Compute.Networks.List, NetworkList, Network, "default")
    20 * compute.instances() >> instanceMock
    10 * instanceMock.insert(_, _, _) >> Mock(Compute.Instances.Insert)
  }

  def getItem(name, Class type) {
    [getName: { name }, getSelfLink: { "selfLink" }]
  }

  def getComputeMock(Class mockType, Class listType, Class listModelType, Class modelType, String name) {
    def mock = Mock(mockType)
    def list = Mock(listType)
    def listModel = Mock(ComputeRequest)
    listModel.getItems() >> [getItems: getItem(name, modelType)]
    list.execute() >> listModel
    mock.list(_, _) >> list
    mock
  }
}
