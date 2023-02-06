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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
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
import java.util.ArrayList;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

@SpringBootTest(classes = {Main.class})
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class ArtifactControllerTest {

  private static final String API_BASE = "/artifacts";
  private static final String API_FETCH = "/fetch";
  private static final String ARTIFACT_DATA = "Some data";

  private MockMvc mockMvc;

  @MockBean private ClouddriverServiceSelector mockClouddriverServiceSelector;

  @MockBean private ClouddriverService mockClouddriverService;

  @MockBean private InputStream mockInputStream;

  @MockBean private TypedByteArray mockBody;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext webApplicationContext;

  @Test
  void TestFetch() throws Exception {

    TypedByteArray responseBody =
        new TypedByteArray(null, objectMapper.writeValueAsBytes(ARTIFACT_DATA));
    Response response =
        new Response("https://localhost", 200, "Some reason", new ArrayList<>(), responseBody);

    when(mockClouddriverServiceSelector.select()).thenReturn(mockClouddriverService);
    when(mockClouddriverService.getArtifactContent(anyMap())).thenReturn(response);

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

    Response response =
        new Response("https://localhost", 200, "Some reason", new ArrayList<>(), mockBody);

    when(mockClouddriverServiceSelector.select()).thenReturn(mockClouddriverService);
    when(mockClouddriverService.getArtifactContent(anyMap())).thenReturn(response);
    when(mockBody.in()).thenReturn(mockInputStream);
    // IOUtil copy default buffer size is 8K, so should expect two read() calls
    when(mockInputStream.read(any(byte[].class)))
        .thenReturn(ARTIFACT_DATA.length())
        .thenReturn(IOUtils.EOF);

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

    mockMvc.perform(asyncDispatch(result)).andDo(print());

    verify(mockInputStream, Mockito.times(1)).close();
  }
}
