/*
 * Copyright 2025 Salesforce, Inc.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@AutoConfigureMockMvc
@SpringBootTest(
    classes = {CredentialsController.class, CredentialsControllerTest.TestConfiguration.class})
public class CredentialsControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockBean private AccountCredentialsProvider accountCredentialsProvider;

  @Test
  @WithAnonymousUser
  public void testGetAccountCredentialsUnauthorized() throws Exception {
    MockHttpServletRequestBuilder builder =
        MockMvcRequestBuilders.get("/credentials/testAccount/authorized")
            .accept(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.toString());

    mockMvc.perform(builder).andDo(print()).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(value = "testUser")
  public void testGetAccountCredentialsAuthorized() throws Exception {
    doReturn(new TestAccountCredentials())
        .when(accountCredentialsProvider)
        .getCredentials("testAccount");
    MockHttpServletRequestBuilder builder =
        MockMvcRequestBuilders.get("/credentials/testAccount/authorized")
            .accept(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.toString());

    MvcResult result = mockMvc.perform(builder).andDo(print()).andReturn();

    assertThat(result.getResponse().getStatus()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }

  static class TestConfiguration {
    @Bean
    CredentialsConfiguration credentialsConfiguration() {
      return new CredentialsConfiguration();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
  }

  static class TestAccountCredentials implements AccountCredentials<Map<String, String>> {
    String name;

    public TestAccountCredentials() {
      name = "testAccount";
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getEnvironment() {
      return "testEnv";
    }

    @Override
    public String getAccountType() {
      return "test";
    }

    @Override
    public Map<String, String> getCredentials() {
      return Map.of("name", name);
    }

    @Override
    public String getCloudProvider() {
      return "testProvider";
    }
  }
}
