/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
      "spring.security.oauth2.client.registration.github.client-id=ec415f229e8f06f6ddb",
      "spring.security.oauth2.client.registration.github.client-secret=53dc2b2125d356c652dfb83fbc0d209de4a9f60"
    })
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class OAuth2Test {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldRedirectOnOauth2Authentication() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/credentials").header(HttpHeaders.AUTHORIZATION, "Bearer access_token"))
            .andDo(print())
            .andReturn();

    assertEquals(302, result.getResponse().getStatus());
  }

  /** Test: Public endpoint should be accessible without authentication */
  @Test
  public void whenAccessingPublicEndpointThenSuccess() throws Exception {
    mockMvc.perform(get("/auth/user")).andExpect(status().isOk());
  }

  /** Test: Secure endpoint should be accessible with OAuth2 authentication */
  @Test
  public void whenAuthenticatedWithOAuth2ThenAccessGranted() throws Exception {
    mockMvc.perform(get("/credentials").with(oauth2Login())).andExpect(status().isOk());
  }

  /** Test: Logout should redirect to home */
  @Test
  public void whenLoggingOutThenRedirectToHome() throws Exception {
    mockMvc.perform(get("/auth/logout")).andExpect(status().is3xxRedirection());
  }
}
