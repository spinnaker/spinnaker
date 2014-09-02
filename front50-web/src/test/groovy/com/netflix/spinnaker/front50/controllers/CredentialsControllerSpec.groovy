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

package com.netflix.spinnaker.front50.controllers

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

import javax.ws.rs.core.MediaType

class CredentialsControllerSpec extends Specification {

  @Shared
  MockMvc mvc

  @Shared
  CredentialsController controller

  @Shared
  AccountCredentialsProvider accountCredentialsProvider

  void setup() {
    accountCredentialsProvider = Mock(AccountCredentialsProvider)
    controller = new CredentialsController(accountCredentialsProvider: accountCredentialsProvider)
    mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()
  }

  void "account list are listed on default endpoint"() {
    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/credentials").accept(MediaType.APPLICATION_JSON)).andReturn()

    then:
    1 * accountCredentialsProvider.getAll() >> {
      def mock = Mock(AccountCredentials)
      mock.getName() >> "test"
      [mock]
    }
    result.response.status == 200
    result.response.contentAsString == '["test"]'
  }
}
