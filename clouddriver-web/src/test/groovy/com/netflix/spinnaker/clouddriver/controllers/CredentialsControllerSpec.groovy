/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration
import com.netflix.spinnaker.clouddriver.controllers.resources.DefaultAccountDefinitionService
import com.netflix.spinnaker.clouddriver.controllers.resources.ManagedAccount
import com.netflix.spinnaker.clouddriver.controllers.resources.MapBackedAccountDefinitionRepository
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

class CredentialsControllerSpec extends Specification {

  @Shared
  MockMvc mvc

  void "named credential names are listed"() {
    setup:

    def objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    def credsRepo = new MapBackedAccountCredentialsRepository()
    def credsProvider = new DefaultAccountCredentialsProvider(credsRepo)
    credsRepo.save("test", new TestNamedAccountCredentials())
    def mvc = MockMvcBuilders.standaloneSetup(new CredentialsController(Optional.empty(), new CredentialsConfiguration(), objectMapper, credsProvider)).build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials").accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    result.response.status == 200

    List<Map> parsedResponse = new JsonSlurper().parseText(result.response.contentAsString) as List

    parsedResponse == [[name: "test", environment: "env", accountType: "acctType", cloudProvider: "testProvider", type: "testProvider", requiredGroupMembership: ["test"], permissions: [READ:["test"], WRITE:["test"]], challengeDestructiveActions: false, primaryAccount: false]]
  }

  /**
   * Test to verify the use of the mandatory type (path) parameter,
   * without passing the optional limit (query) parameter
   */
  void "credentials are listed by type"() {
    setup:

    def objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    def credsRepo = new MapBackedAccountCredentialsRepository()
    def accountDefRepo = new MapBackedAccountDefinitionRepository()
    def credsProvider = new DefaultAccountCredentialsProvider(credsRepo)
    def accountDefSrvc = Optional.of(new DefaultAccountDefinitionService(accountDefRepo))
    accountDefRepo.save(new ManagedAccount("test1", "acctType1"))
    accountDefRepo.save(new ManagedAccount("test2", "acctType1"))
    accountDefRepo.save(new ManagedAccount("test3", "acctType2"))
    accountDefRepo.save(new ManagedAccount("test4", "acctType2"))
    def mvc = MockMvcBuilders.standaloneSetup(new CredentialsController(accountDefSrvc, new CredentialsConfiguration(), objectMapper, credsProvider)).build()

    // path param:
    def acctType = "acctType1"

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials/type/${acctType}").accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    result.response.status == 200

    List<Map> parsedResponse = new JsonSlurper().parseText(result.response.contentAsString) as List

    parsedResponse.every { acct -> acct.accountType == acctType }
  }

  /**
   * Test to verify the use of the type (path)
   * and limit (query) parameters
   */
  void "credentials are listed by type and with limit"() {
    setup:

    def objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    def credsRepo = new MapBackedAccountCredentialsRepository()
    def accountDefRepo = new MapBackedAccountDefinitionRepository()
    def credsProvider = new DefaultAccountCredentialsProvider(credsRepo)
    def accountDefSrvc = Optional.of(new DefaultAccountDefinitionService(accountDefRepo))
    accountDefRepo.save(new ManagedAccount("test1", "acctType1"))
    accountDefRepo.save(new ManagedAccount("test2", "acctType1"))
    accountDefRepo.save(new ManagedAccount("test3", "acctType1"))
    accountDefRepo.save(new ManagedAccount("test4", "acctType1"))
    def mvc = MockMvcBuilders.standaloneSetup(new CredentialsController(accountDefSrvc, new CredentialsConfiguration(), objectMapper, credsProvider)).build()

    // path param:
    def acctType = "acctType1"
    // query param:
    def limit = 2

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials/type/${acctType}")
      .param("limit", "${limit}").accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    result.response.status == 200

    List<Map> parsedResponse = new JsonSlurper().parseText(result.response.contentAsString) as List

    parsedResponse.size() == limit
    parsedResponse.every { acct -> acct.accountType == acctType }
  }

  /**
   * Test to verify the use of the type (path),
   * limit (query) and startingAccountName (query) parameters
   */
  void "credentials are listed by type, startingAccountName and with limit"() {
    setup:

    def objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    def credsRepo = new MapBackedAccountCredentialsRepository()
    def accountDefRepo = new MapBackedAccountDefinitionRepository()
    def credsProvider = new DefaultAccountCredentialsProvider(credsRepo)
    def accountDefSrvc = Optional.of(new DefaultAccountDefinitionService(accountDefRepo))
    accountDefRepo.save(new ManagedAccount("foo", "acctType1"))
    accountDefRepo.save(new ManagedAccount("bar1", "acctType1"))
    accountDefRepo.save(new ManagedAccount("bar2", "acctType1"))
    accountDefRepo.save(new ManagedAccount("baz1", "acctType1"))
    accountDefRepo.save(new ManagedAccount("baz2", "acctType1"))
    accountDefRepo.save(new ManagedAccount("test", "acctType1"))
    def mvc = MockMvcBuilders.standaloneSetup(new CredentialsController(accountDefSrvc, new CredentialsConfiguration(), objectMapper, credsProvider)).build()

    // path param:
    def acctType = "acctType1"
    // query params:
    def limit = 3
    def startingAccountName = "ba"

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials/type/${acctType}")
      .param("limit", "${limit}")
      .param("startingAccountName", "${startingAccountName}")
      .accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    result.response.status == 200

    List<Map> parsedResponse = new JsonSlurper().parseText(result.response.contentAsString) as List

    parsedResponse.size() == limit
    parsedResponse.every { acct -> acct.name.startsWith(startingAccountName) }
  }

  static class TestNamedAccountCredentials extends AbstractAccountCredentials<Map> {

    String name = "test"
    String environment = "env"
    String accountType = "acctType"

    @Override
    Map getCredentials() {
      [access: "a", secret: "b"]
    }

    @Override
    String getCloudProvider() {
      "testProvider"
    }

    @Override
    List<String> getRequiredGroupMembership() {
      ["test"]
    }
  }

}
