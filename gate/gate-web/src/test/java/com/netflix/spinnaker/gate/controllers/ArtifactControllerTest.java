/*
 * Copyright 2022 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import retrofit2.Response;
import retrofit2.mock.Calls;

@SpringBootTest(classes = {Main.class})
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class ArtifactControllerTest {

  private static final String API_BASE = "/artifacts";
  private static final String API_FETCH = "/fetch";
  private static final String ARTIFACT_DATA = "Some data";

  private MockMvc mockMvc;

  @MockBean private ClouddriverServiceSelector mockClouddriverServiceSelector;

  @MockBean private ClouddriverService mockClouddriverService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext webApplicationContext;

  @Test
  void TestFetch() throws Exception {

    Response<ResponseBody> tagsResponse =
        Response.success(
            200,
            ResponseBody.create(
                objectMapper.writeValueAsBytes(ARTIFACT_DATA),
                okhttp3.MediaType.get("text/plain")));

    when(mockClouddriverServiceSelector.select()).thenReturn(mockClouddriverService);
    when(mockClouddriverService.getArtifactContent(anyMap()))
        .thenAnswer(invocation -> Calls.response(tagsResponse));

    mockMvc = webAppContextSetup(webApplicationContext).build();

    MvcResult result =
        mockMvc
            .perform(
                put(API_BASE + API_FETCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("k1", "v1"))))
            .andExpect(request().asyncStarted())
            .andDo(print())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(result))
        .andExpect(content().bytes(objectMapper.writeValueAsBytes(ARTIFACT_DATA)))
        .andDo(print());
  }

  @Test
  void TestFetchInputStreamIsClosed() throws Exception {

    ResponseBody responseBody =
        ResponseBody.create(
            ARTIFACT_DATA.getBytes(StandardCharsets.UTF_8),
            okhttp3.MediaType.get("application/json"));

    Response<ResponseBody> response = Response.success(responseBody);

    try (InputStream actualStream = response.body().byteStream()) {
      assertThat(actualStream).isNotNull();
    }
  }
}
