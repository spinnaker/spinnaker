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

package com.netflix.spinnaker.kato.gce.deploy.handlers

import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceList
import com.google.api.services.compute.model.MachineType
import com.google.api.services.compute.model.MachineTypeList
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.NetworkList
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployHandlerSpec extends Specification {

  @Shared
  BasicGoogleDeployHandler handler

  void setupSpec() {
    this.handler = new BasicGoogleDeployHandler()
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "handler supports basic deploy description type"() {
    given:
    def description = new BasicGoogleDeployDescription()

    expect:
    handler.handles description
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
    def description = new BasicGoogleDeployDescription(application: "app", stack: "stack", image: "image", instanceType: "f1-micro", zone: "us-central1-b", credentials: credentials)

    when:
    handler.handle(description, [])

    then:
    10 * compute.machineTypes() >> getComputeMock(Compute.MachineTypes, Compute.MachineTypes.List, MachineTypeList, MachineType, description.instanceType)
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
