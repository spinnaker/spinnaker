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

package com.netflix.spinnaker.oort.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.oort.applications.Application
import com.netflix.spinnaker.oort.applications.ApplicationProvider
import com.netflix.spinnaker.oort.clusters.Clusters
import com.netflix.spinnaker.oort.security.NamedAccountCredentials
import com.netflix.spinnaker.oort.security.NamedAccountCredentialsProvider
import org.springframework.context.MessageSource
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Shared
import spock.lang.Specification

class ApplicationControllerSpec extends Specification {

  @Shared
  MockMvc mvc

  void setupSpec() {
    mvc = MockMvcBuilders.standaloneSetup(ApplicationController).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()
  }

  void "request to controller uses applicationProviders to resolve applications"() {
    setup:
    def controller = new ApplicationController()
    def messageSource = Mock(MessageSource)
    def mockAppProvider = Mock(ApplicationProvider)
    controller.applicationProviders = [mockAppProvider]
    controller.messageSource = messageSource
    def mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications")).andReturn()

    then:
    1 * mockAppProvider.list() >> { [] }

    when:
    mvc.perform(MockMvcRequestBuilders.get("/applications/foo")).andReturn()

    then:
    1 * mockAppProvider.get("foo") >> { null }
  }

  void "requested application is transformed to ApplicationViewModel"() {
    setup:
    def objectMapper = new ObjectMapper()
    def controller = new ApplicationController()
    def messageSource = Mock(MessageSource)
    def mockAppProvider = Mock(ApplicationProvider)
    def accountProvider = Mock(NamedAccountCredentialsProvider)
    controller.applicationProviders = [mockAppProvider]
    controller.messageSource = messageSource
    controller.namedAccountCredentialsProvider = accountProvider
    def mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/applications/foo")).andReturn()
    def resp = objectMapper.readValue(result.response.contentAsString, Map)

    then:
    1 * accountProvider.list() >> {
      def mock = Mock(NamedAccountCredentials)
      mock.getName() >> "test"
      [mock]
    }
    1 * mockAppProvider.get("foo") >> {
      def app = Mock(Application)
      app.getName() >> "foo"
      app.getClusters("test") >> new Clusters()
      app.getAttributes() >> [pdfKeyId: "1234"]
      app
    }
    result.response.status == 200
    resp.name == "foo"
    0 == resp.clusterCount
    0 == resp.serverGroupCount
    0 == resp.instanceCount
  }

  void "failure to lookup an application gets a meaningful error message"() {
    setup:
    def objectMapper = new ObjectMapper()
    def controller = new ApplicationController()
    def messageSource = Mock(MessageSource)
    def mockAppProvider = Mock(ApplicationProvider)
    controller.applicationProviders = [mockAppProvider]
    controller.messageSource = messageSource
    def mvc = MockMvcBuilders.standaloneSetup(controller).setMessageConverters(new MappingJackson2HttpMessageConverter()).build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/applications/foo")).andReturn()
    def resp = objectMapper.readValue(result.response.contentAsString, Map)

    then:
    result.response.status == 404
    resp.error == "Application not found"
  }

}
