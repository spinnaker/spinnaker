/*
 * Copyright 2026 Harness, Inc.
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

import static com.netflix.spinnaker.kork.common.Header.APPLICATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreStorer;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.Main;
import com.netflix.spinnaker.rosco.executor.BakePoller;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = "spring.application.name = rosco")
class V2BakeryControllerArtifactStoreTest {

  private static final String STORED_REFERENCE = "ref://my-app/0123456789abcdef";

  private MockMvc webAppMockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  /** Copies X-SPINNAKER-* headers into the MDC, where ArtifactStore reads the application. */
  @Autowired private AuthenticatedRequestFilter authenticatedRequestFilter;

  @MockitoBean private BakePoller bakePoller;

  @MockitoBean private HelmBakeManifestService helmBakeManifestService;

  @MockitoBean private ArtifactStoreStorer artifactStoreStorer;

  @BeforeEach
  void init() {
    webAppMockMvc =
        webAppContextSetup(webApplicationContext).addFilters(authenticatedRequestFilter).build();
  }

  @Test
  void bakeResponseIsStoredInArtifactStore() throws Exception {
    Artifact baked =
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
            .name("baked-manifest")
            .reference(
                Base64.getEncoder()
                    .encodeToString("kind: ConfigMap".getBytes(StandardCharsets.UTF_8)))
            .build();
    when(helmBakeManifestService.handles("HELM3")).thenReturn(true);
    when(helmBakeManifestService.requestType()).thenReturn(HelmBakeManifestRequest.class);
    when(helmBakeManifestService.bake(any())).thenReturn(baked);
    when(artifactStoreStorer.store(any(Artifact.class), any(ArtifactDecorator[].class)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0, Artifact.class).toBuilder()
                    .type(ArtifactTypes.REMOTE_BASE64.getMimeType())
                    .reference(STORED_REFERENCE)
                    .build());

    webAppMockMvc
        .perform(
            post("/api/v2/manifest/bake/HELM3")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header(APPLICATION.getHeader(), "my-app")
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value(ArtifactTypes.REMOTE_BASE64.getMimeType()))
        .andExpect(jsonPath("$.reference").value(STORED_REFERENCE))
        .andExpect(jsonPath("$.name").value("baked-manifest"));

    verify(artifactStoreStorer).store(any(Artifact.class), any(ArtifactDecorator[].class));
  }
}
