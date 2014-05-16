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

package com.netflix.bluespar.kato.com.netflix.asgard.kato.controllers

import com.netflix.bluespar.kato.controllers.CredentialsController
import com.netflix.bluespar.kato.security.DefaultNamedAccountCredentialsHolder
import com.netflix.bluespar.kato.security.NamedAccountCredentials
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
    def namedAccountCredentialsHolder = new DefaultNamedAccountCredentialsHolder()
    namedAccountCredentialsHolder.put("test", new TestNamedAccountCredentials())
    def mvc = MockMvcBuilders.standaloneSetup(new CredentialsController(namedAccountCredentialsHolder: namedAccountCredentialsHolder)).build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials").accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    result.response.status == 200
    result.response.contentAsString == '["test"]'
  }

  static class TestNamedAccountCredentials implements NamedAccountCredentials<Map> {

    @Override
    Map getCredentials() {
      [access: "a", secret: "b"]
    }
  }

}
