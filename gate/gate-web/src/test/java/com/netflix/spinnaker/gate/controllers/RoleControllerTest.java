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
import com.netflix.spinnaker.gate.services.RoleService;
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
public class RoleControllerTest {

  private MockWebServer server;
  private MockMvc mockMvc;

  @Mock private ClouddriverService clouddriverService;

  private RoleService roleService;

  private ObjectMapper objectMapper;

  @BeforeEach
  public void setup() {
    server = new MockWebServer();
    roleService = new RoleService(clouddriverService);
    RoleController controller = new RoleController();
    controller.setRoleService(roleService);
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
    List<Map> roleList = new ArrayList<>();

    when(clouddriverService.getRoles("aws")).thenReturn(callResponse);

    mockMvc
        .perform(get("/roles/aws").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(roleList)));

    verify(clouddriverService).getRoles("aws");
  }

  @Test
  public void shouldReturnNonEmptyResponse() throws Exception {
    List<Map> roleList = new ArrayList<>();

    Map<String, String> role1 = new HashMap<>();
    role1.put("accountName", "my-account-1");
    role1.put("id", "arn:aws:iam::0000000000000:role/role-ecs");
    role1.put("name", "role-ecs");
    role1.put(
        "trustRelationships",
        List.of(
                Map.of(
                    "type", "Service",
                    "value", "logs.amazonaws.com"),
                Map.of(
                    "type", "Service",
                    "value", "ecs-tasks.amazonaws.com"))
            .toString());

    Map<String, String> role2 = new HashMap<>();
    role2.put("accountName", "my-account-2");
    role2.put("id", "arn:aws:iam::1111111111111:role/role-ecs");
    role2.put("name", "role-ecs");
    role2.put(
        "trustRelationships",
        List.of(
                Map.of(
                    "type", "Service",
                    "value", "logs.amazonaws.com"),
                Map.of(
                    "type", "Service",
                    "value", "ecs-tasks.amazonaws.com"))
            .toString());

    roleList.add(role1);
    roleList.add(role2);

    Call<List<Map>> callResponse = Calls.response(roleList);

    when(clouddriverService.getRoles("ecs")).thenReturn(callResponse);

    mockMvc
        .perform(get("/roles/ecs").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(roleList)));

    verify(clouddriverService).getRoles("ecs");
  }
}
