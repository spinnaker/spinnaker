/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.EcsClusterService
import com.netflix.spinnaker.gate.controllers.ecs.EcsClusterController
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.squareup.okhttp.mockwebserver.MockWebServer
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification
import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class EcsClusterControllerSpec extends Specification {

  def server = new MockWebServer()
  MockMvc mockMvc
  def cloudDriverService
  def ecsClusterService

  void cleanup() {
    server.shutdown()
  }

  void setup(){
    given:
    cloudDriverService = Mock(ClouddriverService)
    ecsClusterService = new EcsClusterService(cloudDriverService)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new EcsClusterController(ecsClusterService: ecsClusterService)).build()
  }

  @Unroll
  void 'Describe Cluster API should return 200 with cluster descriptions'() {

    def apiResponse = [
      [ activeServicesCount : '2',
        attachments :[],
        capacityProviders : [],
        clusterArn :'arn:aws:ecs:us-west-2:123123123:cluster/spinnaker',
        clusterName : 'spinnaker',
        defaultCapacityProviderStrategy : [],
        pendingTasksCount : '3',
        registeredContainerInstancesCount : '0',
        runningTasksCount : '0',
        settings :[[ name :'containerInsights',
                       value : 'disabled']],
      ],
    ]

    1 * cloudDriverService.getEcsClusterDescriptions('ecs-my-aws-devel-acct', 'us-west-2') >> apiResponse
    when:
    MockHttpServletResponse response = mockMvc.perform(
      get("/ecs/ecsClusterDescriptions/ecs-my-aws-devel-acct/us-west-2").contentType(MediaType.APPLICATION_JSON)
    ).andReturn().response

    then:
    response.status == 200
    new JsonSlurper().parseText(response.contentAsString) == apiResponse
  }

  @Unroll
  void 'Describe Cluster API should return 200 with empty response'() {
    def apiResponse = []

    1 * cloudDriverService.getEcsClusterDescriptions('ecs-my-aws-devel-acct', 'us-west-2') >> apiResponse
    when:
    MockHttpServletResponse response = mockMvc.perform(
      get("/ecs/ecsClusterDescriptions/ecs-my-aws-devel-acct/us-west-2").contentType(MediaType.APPLICATION_JSON)
    ).andReturn().response

    then:
    response.status == 200
    new JsonSlurper().parseText(response.contentAsString) == apiResponse
  }
}
