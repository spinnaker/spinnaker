/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.listeners.ConfigurationRefreshListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@SpringBootTest(classes = {Main.class})
@TestPropertySource(
    properties = {
      "redis.enabled = false",
      "sql.enabled = false",
      "spring.application.name = clouddriver",
      "kubernetes.enabled = true",
      "management.endpoints.web.exposure.include = refresh",
      "kubernetes.customPropertyBindingEnabled = true",
      "spring.cloud.bootstrap.enabled = true",
      "spring.cloud.config.server.bootstrap = true",
      "spring.profiles.active = native",
      "spring.cloud.config.server.native.search-locations = classpath:/"
    })
public class KubernetesCustomPropertyBindingRefreshTest {
  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @SpyBean private ConfigurationRefreshListener listener;

  @BeforeEach
  void setup(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  public void testRefreshScopeRefreshedEvent() throws Exception {
    mockMvc.perform(post("/refresh")).andExpect(status().isOk());

    verify(listener).onApplicationEvent(any(RefreshScopeRefreshedEvent.class));
  }
}
