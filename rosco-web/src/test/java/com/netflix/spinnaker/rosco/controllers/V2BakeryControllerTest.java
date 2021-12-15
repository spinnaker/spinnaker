/*
 * Copyright 2021 Salesforce, Inc.
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
package com.netflix.spinnaker.rosco.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.rosco.Main;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import retrofit.client.Response;
import retrofit.mime.TypedString;

@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = "spring.application.name = rosco")
class V2BakeryControllerTest {

  private MockMvc webAppMockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @MockBean ClouddriverService clouddriverService;

  @Autowired ObjectMapper objectMapper;

  private HelmBakeManifestRequest bakeManifestRequest;

  @BeforeEach
  private void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc = webAppContextSetup(webApplicationContext).build();

    bakeManifestRequest = new HelmBakeManifestRequest();
    Artifact chartArtifact = Artifact.builder().name("test-artifact").version("3").build();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact));
  }

  @Test
  void testSpinnakerServerException() throws Exception {
    SpinnakerServerException clouddriverException = mock(SpinnakerServerException.class);
    when(clouddriverException.getMessage()).thenReturn("message from clouddriver");
    when(clouddriverService.fetchArtifact(any(Artifact.class))).thenThrow(clouddriverException);

    webAppMockMvc
        .perform(
            post("/api/v2/manifest/bake/HELM2")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content(objectMapper.writeValueAsString(bakeManifestRequest)))
        .andDo(print())
        .andExpect(status().isInternalServerError());
  }

  @Test
  void testSpinnakerHttpExceptionWith404Response() throws Exception {
    SpinnakerHttpException clouddriverException = makeSpinnakerHttpException(404);
    when(clouddriverService.fetchArtifact(any(Artifact.class))).thenThrow(clouddriverException);

    webAppMockMvc
        .perform(
            post("/api/v2/manifest/bake/HELM2")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content(objectMapper.writeValueAsString(bakeManifestRequest)))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

  private static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    SpinnakerHttpException spinnakerHttpException = mock(SpinnakerHttpException.class);
    when(spinnakerHttpException.getMessage()).thenReturn("message");

    // Response is a final class, so straightforward mocking fails.
    String url = "https://some-url";
    Response response =
        new Response(
            url,
            status,
            "arbitrary reason",
            List.of(),
            new TypedString("{ message: \"unused message due to above mock\" }"));

    when(spinnakerHttpException.getResponse()).thenReturn(response);
    return spinnakerHttpException;
  }
}
