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

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.aws.model.CloudFormationStack
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonCloudFormationProvider
import groovy.transform.Immutable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.rest.webmvc.ResourceNotFoundException
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.mock.DetachedMockFactory

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [CloudFormationController])
@ContextConfiguration(classes = [CloudFormationController])
@AutoConfigureMockMvc(secure=false)
class CloudFormationControllerSpec extends Specification {

  @Autowired
  protected MockMvc mvc

  @Autowired
  AmazonCloudFormationProvider cloudFormationProvider

  def "request a list of stacks returns all the stacks for a given account (any region)"() {
    given:
    def accountName = 'aws-account-name'
    cloudFormationProvider.list(accountName, '*') >> [ new CloudFormationStackTest(accountName: accountName) ]

    when:
    def results = mvc.perform(get("/aws/cloudFormation/stacks?accountName=$accountName"))

    then:
    results.andExpect(status().is2xxSuccessful())
    results.andExpect(jsonPath('$[0].accountName').value(accountName))
  }

  def "request a list of stacks returns all the stacks for a given account filtering by region (if specified)"() {
    given:
    def accountName = 'aws-account-name'
    def region = 'region'
    cloudFormationProvider.list(accountName, region) >> [ new CloudFormationStackTest(accountName: accountName, region: region) ]

    when:
    def results = mvc.perform(get("/aws/cloudFormation/stacks?accountName=$accountName&region=$region"))

    then:
    results.andExpect(status().is2xxSuccessful())
    results.andExpect(jsonPath('$[0].accountName').value(accountName))
    results.andExpect(jsonPath('$[0].region').value(region))
  }

  def "requesting a single stack by stackId"() {
    given:
    def stackId = "arn:cloudformation:stack/name"
    cloudFormationProvider.get(stackId) >> Optional.of(new CloudFormationStackTest(stackId: stackId))

    when:
    def results = mvc.perform(get("/aws/cloudFormation/stacks/$stackId"))

    then:
    results.andExpect(status().is2xxSuccessful())
    results.andExpect(jsonPath('$.stackId').value(stackId))
  }

  def "requesting a non existing stack returns a 404"() {
    given:
    def stackId = "arn:cloudformation:non-existing"
    cloudFormationProvider.get(stackId) >> { throw new ResourceNotFoundException() }

    when:
    def results = mvc.perform(get("/aws/cloudFormation/stacks/$stackId"))

    then:
    results.andExpect(status().is(404))
  }

  @Immutable
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  class CloudFormationStackTest implements CloudFormationStack {
    final String stackId
    final Map<String, String> tags
    final Map<String, String> outputs
    final String stackName
    final String region
    final String stackStatus
    final String stackStatusReason
    final String accountName
    final String accountId
    final Date creationTime
  }

  @TestConfiguration
  static class StubConfig {

    DetachedMockFactory detachedMockFactory = new DetachedMockFactory()

    @Bean
    AmazonCloudFormationProvider provider() {
      detachedMockFactory.Stub(AmazonCloudFormationProvider)
    }
  }

}
