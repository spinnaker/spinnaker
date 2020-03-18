/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.config.SpinnakerExtensionsConfigProperties
import com.netflix.spinnaker.gate.services.TaskService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.when
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [PluginController])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [PluginController, GenericExceptionHandlers, SpinnakerExtensionsConfigProperties])
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.config.location=classpath:gate-test.yml"])
class PluginControllerSpec extends Specification {

  @Autowired
  private MockMvc mockMvc

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  SpinnakerExtensionsConfigProperties spinnakerExtensionsConfigProperties

  @MockBean
  private TaskService taskService

  @MockBean
  private Front50Service front50Service

  private Map requestContent = ['name': 'test plugin', provider: 'Test Co']

  def 'should load configuration bean with expected values'() {
    expect:
    spinnakerExtensionsConfigProperties.applicationName == 'spinnakerpluginstest'
  }

  def 'upsert api should fail when sent no content-type'() {
    expect:
    this.mockMvc.perform(MockMvcRequestBuilders.post("/pluginInfo")).andExpect(status().isUnsupportedMediaType())
  }

  def 'upsert api should fail when sent no content'() {
    expect:
    this.mockMvc.perform(MockMvcRequestBuilders.post("/pluginInfo")
                .header('Content-Type', "application/json"))
                .andExpect(status().isBadRequest())
  }

  def 'upsert api should succeed when sent content'() {
    setup:
    when(taskService.create(any())).thenReturn(['ref': 'tasks/ref/1'])

    expect:
    this.mockMvc.perform(MockMvcRequestBuilders.post("/pluginInfo")
                .header('Content-Type', "application/json")
                .content(objectMapper.writeValueAsString(requestContent)))
                .andExpect(status().isAccepted())
  }

  def 'upsert api should succeed with put method'() {
    setup:
    when(taskService.create(any())).thenReturn(['ref': 'tasks/ref/2'])

    expect:
    this.mockMvc.perform(MockMvcRequestBuilders.put("/pluginInfo")
      .header('Content-Type', "application/json")
      .content(objectMapper.writeValueAsString(requestContent)))
      .andExpect(status().isAccepted())
  }

  def 'delete api should succeed'() {
    setup:
    when(taskService.create(any(Map.class))).thenAnswer { taskData ->
      Map m = taskData.getArgument(0)
      assert m.get('job')[0].get('pluginInfoId').toString().equalsIgnoreCase('test.plugin.id')
      (['ref': '/tasks/ref/3'])
    }

    expect:
    this.mockMvc.perform(MockMvcRequestBuilders.delete("/pluginInfo/test.plugin.id")
      .header('Content-Type', "application/json"))
      .andExpect(status().isAccepted())
  }

  def 'get api should succeed'() {
    setup:
    when(front50Service.getPluginInfo(any())).thenReturn([['Id': 'test-plugin-id']])

    expect:
    this.mockMvc.perform(MockMvcRequestBuilders.get("/pluginInfo")
      .header('Content-Type', "application/json"))
      .andExpect(status().isOk())
  }

}
