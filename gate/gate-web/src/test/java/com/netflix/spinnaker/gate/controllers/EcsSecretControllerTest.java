/*
 * Copyright 2025 Harness, Inc.
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.controllers.ecs.EcsSecretController;
import com.netflix.spinnaker.gate.services.EcsSecretService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import retrofit2.Call;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class EcsSecretControllerTest {

  private MockWebServer server;
  private MockMvc mockMvc;

  @Mock private ClouddriverService clouddriverService;

  private EcsSecretService ecsSecretService;

  private ObjectMapper objectMapper;

  @BeforeEach
  public void setup() {
    server = new MockWebServer();
    ecsSecretService = new EcsSecretService(clouddriverService);
    EcsSecretController controller = new EcsSecretController();
    controller.setEcsSecretService(ecsSecretService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
  }

  @AfterEach
  public void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  public void shouldReturnEmptyResponse() throws Exception {
    Call<List<Map>> callResponse = Calls.response(new ArrayList<>());
    List<Map> secretsList = new ArrayList<>();

    when(clouddriverService.getAllEcsSecrets()).thenReturn(callResponse);

    mockMvc
        .perform(get("/ecs/secrets").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(secretsList)));

    verify(clouddriverService).getAllEcsSecrets();
  }

  @Test
  public void shouldReturnNonEmptyResponse() throws Exception {
    List<Map> secretsList = new ArrayList<>();

    Map<String, String> secret1 = new HashMap<>();
    secret1.put("account", "my-account-1");
    secret1.put("arn", "arn:aws:secretsmanager:us-west-2:111111111111:secret:my-secret-1");
    secret1.put("name", "my-secret-1");
    secret1.put("region", "us-west-2");

    Map<String, String> secret2 = new HashMap<>();
    secret2.put("account", "my-account-2");
    secret2.put("arn", "arn:aws:secretsmanager:us-west-2:111111111111:secret:my-secret-2");
    secret2.put("name", "my-secret-2");
    secret2.put("region", "us-east-1");

    secretsList.add(secret1);
    secretsList.add(secret2);

    // Setup mock response
    Call<List<Map>> callResponse = Calls.response(secretsList);
    when(clouddriverService.getAllEcsSecrets()).thenReturn(callResponse);

    // Perform the request and verify
    mockMvc
        .perform(get("/ecs/secrets"))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(secretsList)));

    // Verify the clouddriver service was called
    verify(clouddriverService).getAllEcsSecrets();
  }
}
