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
import com.netflix.spinnaker.gate.controllers.ecs.EcsServerGroupEventsController;
import com.netflix.spinnaker.gate.services.EcsServerGroupEventsService;
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
public class EcsServerGroupEventsControllerTest {

  private MockWebServer server;
  private MockMvc mockMvc;

  @Mock private ClouddriverService clouddriverService;

  private EcsServerGroupEventsService ecsServerGroupEventsService;

  private ObjectMapper objectMapper;

  @BeforeEach
  public void setup() {
    server = new MockWebServer();
    ecsServerGroupEventsService = new EcsServerGroupEventsService(clouddriverService);
    EcsServerGroupEventsController controller = new EcsServerGroupEventsController();
    controller.setEcsServerGroupEventsService(ecsServerGroupEventsService);
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
    List<Map> eventsList = new ArrayList<>();

    when(clouddriverService.getServerGroupEvents(
            "test", "ecs-account", "test-app-v001", "us-west-2", "ecs"))
        .thenReturn(callResponse);

    mockMvc
        .perform(
            get("/applications/test/serverGroups/ecs-account/test-app-v001/events?region=us-west-2&provider=ecs")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(eventsList)));

    verify(clouddriverService)
        .getServerGroupEvents("test", "ecs-account", "test-app-v001", "us-west-2", "ecs");
  }

  @Test
  public void shouldReturnNonEmptyResponse() throws Exception {
    List<Map> eventsList = new ArrayList<>();

    Map<String, Object> event1 = new HashMap<>();
    event1.put("createdAt", 1760710039170L);
    event1.put("id", "925a1041-1105-42f5-8fb4-d67e4048488b");
    event1.put("message", "(service test-app-v002) has reached a steady state.");
    event1.put("status", "Success");

    Map<String, Object> event2 = new HashMap<>();
    event2.put("createdAt", 1760710039169L);
    event2.put("id", "c2b96d8e-b270-4f3d-8dfd-65747d333635");
    event2.put(
        "message",
        "(service test-app-v002) (deployment ecs-svc/3590050727904051367) deployment completed.");
    event2.put("status", "Transition");

    Map<String, Object> event3 = new HashMap<>();
    event3.put("createdAt", 1760710029102L);
    event3.put("id", "681b8bac-72c1-4820-9cb4-46766946c741");
    event3.put(
        "message",
        "(service test-app-v002) has started 3 tasks: (task 8f7c499988e94ac8af15a512b2808d6d) (task d224dde6aafb440a99dfac09edc8763e) (task fa5a0e60c1284febb1fd162887861361).");
    event3.put("status", "Transition");

    Map<String, Object> event4 = new HashMap<>();
    event4.put("createdAt", 1760710018684L);
    event4.put("id", "252dd4fa-f038-48fb-9c85-e667400add51");
    event4.put(
        "message",
        "(service test-app-v002) has started 1 tasks: (task 2de4a18639e948c3b7a2a4ec37840d4f).");
    event4.put("status", "Transition");

    eventsList.add(event1);
    eventsList.add(event2);
    eventsList.add(event3);
    eventsList.add(event4);

    Call<List<Map>> callResponse = Calls.response(eventsList);
    when(clouddriverService.getServerGroupEvents(
            "test", "ecs-account", "test-app-v002", "us-west-2", "ecs"))
        .thenReturn(callResponse);

    mockMvc
        .perform(
            get("/applications/test/serverGroups/ecs-account/test-app-v002/events?region=us-west-2&provider=ecs")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(eventsList)));

    verify(clouddriverService)
        .getServerGroupEvents("test", "ecs-account", "test-app-v002", "us-west-2", "ecs");
  }
}
