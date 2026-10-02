/*
 * Copyright 2020 Avast Software, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers;

import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.helm.HelmArtifactCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code @AutoConfigureMockMvc} wires the real, fully-configured MockMvc instance -- including
 * Spring Security's filter chain (so {@code @PreAuthorize}/{@code @PostFilter} on
 * ArtifactController/ArtifactCredentialsRepository see a real, at-minimum-anonymous Authentication
 * instead of none at all) and every registered servlet Filter bean, such as WebConfig's
 * AuthenticatedRequestFilter -- the same way the real running app assembles its filter chain,
 * rather than hand-building a partial one.
 */
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "redis.enabled = false",
      "sql.enabled = false",
      "spring.application.name = clouddriver",
      "artifacts.helm.enabled = true"
    })
public class ArtifactControllerSpec {

  @Autowired private MockMvc mvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CredentialsRepository<HelmArtifactCredentials> helmCredentials;

  @Test
  public void testFetchWithMisconfiguredArtifact() throws Exception {
    Artifact misconfiguredArtifact = Artifact.builder().name("foo").build();

    // Capture the log messages that ArtifactCredentialsRepository generates,
    // since that's the class that logs a message when it detects a
    // misconfigured artifact.
    MemoryAppender memoryAppender = new MemoryAppender(ArtifactCredentialsRepository.class);

    // Use USER (i.e. X-SPINNAKER-HEADER) as a request header to match what
    // logback includes in log messages for assertions in this test to work.
    String userValue = "some user";

    MvcResult result =
        mvc.perform(
                put("/artifacts/fetch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(USER.getHeader(), userValue)
                    .content(objectMapper.writeValueAsString(misconfiguredArtifact)))
            .andReturn();

    mvc.perform(asyncDispatch(result))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(content().string(is(emptyString())));

    List<String> userMessages = memoryAppender.layoutSearch("[" + userValue + "]", Level.DEBUG);
    assertThat(userMessages).hasSize(1);
  }

  @Test
  public void testArtifactNames() throws Exception {
    List<String> names = ImmutableList.of("artifact1", "artifact2");
    HelmArtifactCredentials credentials = Mockito.mock(HelmArtifactCredentials.class);
    Mockito.when(credentials.getName()).thenReturn("my-account");
    Mockito.when(credentials.getType()).thenReturn(HelmArtifactCredentials.CREDENTIALS_TYPE);
    Mockito.when(credentials.handlesType("helm/chart")).thenReturn(true);
    Mockito.when(credentials.getArtifactNames()).thenReturn(names);
    helmCredentials.save(credentials);

    mvc.perform(
            get("/artifacts/account/{accountName}/names", credentials.getName())
                .param("type", "helm/chart"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0]", Matchers.is(names.get(0))))
        .andExpect(jsonPath("$[1]", Matchers.is(names.get(1))));

    // We also don't expect to find an account that can support type artifacts-helm
    mvc.perform(
            get("/artifacts/account/{accountName}/names", credentials.getName())
                .param("type", HelmArtifactCredentials.CREDENTIALS_TYPE))
        .andExpect(status().isNotFound());
  }

  @Test
  public void testArtifactVersions() throws Exception {
    final String artifactName = "my-artifact";
    List<String> versions = ImmutableList.of("version1", "version2");
    HelmArtifactCredentials credentials = Mockito.mock(HelmArtifactCredentials.class);
    Mockito.when(credentials.getName()).thenReturn("my-account");
    Mockito.when(credentials.getType()).thenReturn(HelmArtifactCredentials.CREDENTIALS_TYPE);
    Mockito.when(credentials.handlesType("helm/chart")).thenReturn(true);
    Mockito.when(credentials.getArtifactVersions(artifactName)).thenReturn(versions);
    helmCredentials.save(credentials);

    mvc.perform(
            get("/artifacts/account/{accountName}/versions", credentials.getName())
                .param("type", "helm/chart")
                .param("artifactName", artifactName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(2)))
        .andExpect(jsonPath("$[0]", Matchers.is(versions.get(0))))
        .andExpect(jsonPath("$[1]", Matchers.is(versions.get(1))));

    // We also don't expect to find an account that can support type artifacts-helm
    mvc.perform(
            get("/artifacts/account/{accountName}/versions", credentials.getName())
                .param("type", HelmArtifactCredentials.CREDENTIALS_TYPE)
                .param("artifactName", artifactName))
        .andExpect(status().isNotFound());
  }
}
