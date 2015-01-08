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


package com.netflix.spinnaker.kato.com.netflix.asgard.kato.controllers

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kato.controllers.CredentialsController
import groovy.json.JsonSlurper
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

import javax.ws.rs.core.MediaType

class CredentialsControllerSpec extends Specification {

  @Shared
  MockMvc mvc

  void "named credential names are listed"() {
    setup:
    def credsRepo = new MapBackedAccountCredentialsRepository()
    def credsProvider = new DefaultAccountCredentialsProvider(credsRepo)
    credsRepo.save("test", new TestNamedAccountCredentials())
    def mvc = MockMvcBuilders.standaloneSetup(new CredentialsController(accountCredentialsProvider: credsProvider)).build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials").accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    result.response.status == 200

    List<Map> parsedResponse = new JsonSlurper().parseText(result.response.contentAsString) as List

    parsedResponse == [[name: "test", type: "unknown"]]


    // TODO(duftler): Switch to this when updated amos is available in kato.
//    parsedResponse == [[name: "test", type: "testProvider"]]
  }

  static class TestNamedAccountCredentials implements AccountCredentials<Map> {

    String name = "test"

    @Override
    Map getCredentials() {
      [access: "a", secret: "b"]
    }

    // TODO(duftler): Uncomment this when updated amos is available in kato.
//    @Override
    String getProvider() {
      "testProvider"
    }
  }

}
