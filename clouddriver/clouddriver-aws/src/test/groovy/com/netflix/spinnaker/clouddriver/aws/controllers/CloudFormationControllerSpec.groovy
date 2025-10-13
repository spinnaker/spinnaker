/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.clouddriver.aws.controllers

import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormationStack
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonCloudFormationProvider
import org.springframework.test.web.servlet.MockMvc
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CloudFormationControllerSpec extends Specification {

  MockMvc mockMvc

  AmazonCloudFormationProvider cloudFormationProvider = Mock(AmazonCloudFormationProvider)

  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(
      new CloudFormationController(cloudFormationProvider)
    ).build()
  }

  def "request a list of stacks returns all the stacks for a given account (any region)"() {
    given:
    def accountName = 'aws-account-name'
    cloudFormationProvider.list(accountName, '*') >> [ new AmazonCloudFormationStack(accountName: accountName) ]

    when:
    def results = mockMvc.perform(get("/aws/cloudFormation/stacks?accountName=$accountName"))

    then:
    results.andExpect(status().is2xxSuccessful())
    results.andExpect(jsonPath('$[0].accountName').value(accountName))
  }

  def "request a list of stacks returns all the stacks for a given account filtering by region (if specified)"() {
    given:
    def accountName = 'aws-account-name'
    def region = 'region'
    cloudFormationProvider.list(accountName, region) >> [ new AmazonCloudFormationStack(accountName: accountName, region: region) ]

    when:
    def results = mockMvc.perform(get("/aws/cloudFormation/stacks?accountName=$accountName&region=$region"))

    then:
    results.andExpect(status().is2xxSuccessful())
    results.andExpect(jsonPath('$[0].accountName').value(accountName))
    results.andExpect(jsonPath('$[0].region').value(region))
  }

  def "requesting a single stack by stackId"() {
    given:
    def stackId = "arn:cloudformation:stack/name"
    cloudFormationProvider.get(stackId) >> Optional.of(new AmazonCloudFormationStack(stackId: stackId))

    when:
    def results = mockMvc.perform(get("/aws/cloudFormation/stacks/stack?stackId=$stackId"))

    then:
    results.andExpect(status().is2xxSuccessful())
    results.andExpect(jsonPath('$.stackId').value(stackId))
  }

  def "requesting a non existing stack returns a 404"() {
    given:
    def stackId = "arn:cloudformation:non-existing"
    cloudFormationProvider.get(stackId) >> { throw new NotFoundException() }

    when:
    mockMvc.perform(get("/aws/cloudFormation/stacks/stack?stackId=$stackId"))

    then:
    thrown(Exception) //loosened because we removed the dependency on spring data rest
  }

}
