/*
 * Copyright 2025 Salesforce, Inc.
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
package com.netflix.spinnaker.gate.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.kork.common.Header;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import retrofit2.mock.Calls;

@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "header.enabled=true",
      // "security.debug=true",
      "logging.level.org.springframework.security=DEBUG",
      "spring.config.location=classpath:gate-test.yml",
      "services.front50.applicationRefreshInitialDelayMs=3600000"
    })
public class AuthControllerTest {

  private static final String USERNAME = "test@email.com";

  @Autowired private WebApplicationContext webApplicationContext;

  @MockBean ClouddriverService clouddriverService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  private MockMvc webAppMockMvc;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();

    // To keep DefaultProviderLookupService.loadAccounts happy
    when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
  }

  @Test
  void testGetUser() throws Exception {
    webAppMockMvc
        .perform(
            get("/auth/user")
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .header(Header.USER.getHeader(), USERNAME))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(USERNAME))
        .andExpect(jsonPath("$.username").value(USERNAME))
        .andExpect(jsonPath("$.firstName").value(IsNull.nullValue()))
        .andExpect(jsonPath("$.lastName").value(IsNull.nullValue()))
        .andExpect(jsonPath("$.roles").isEmpty())
        .andExpect(jsonPath("$.allowedAccounts").isEmpty())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.authorities").isEmpty())
        .andExpect(jsonPath("$.accountNonExpired").value(true))
        .andExpect(jsonPath("$.accountNonLocked").value(true))
        .andExpect(jsonPath("$.credentialsNonExpired").value(true));
  }

  // TODO: expect anonymous once the code is set up to do that
  // @Test
  // void testGetUserWithNoUser() throws Exception {
  //  webAppMockMvc
  //      .perform(
  //          get("/auth/user")
  //              .accept(MediaType.APPLICATION_JSON_VALUE)
  //              .characterEncoding(StandardCharsets.UTF_8.toString()))
  //      .andDo(print())
  //      .andExpect(status().isOk());
  // }
}
