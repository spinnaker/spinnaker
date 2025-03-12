/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests.cloudfoundry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class CloudFoundryBakeManifestServiceTest {

  private ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
  private JobExecutor jobExecutor = mock(JobExecutor.class);
  private CloudFoundryBakeManifestService cloudFoundryBakeManifestService =
      new CloudFoundryBakeManifestService(jobExecutor, artifactDownloader);

  @Test
  public void shouldResolveManifestWithSimpleVars() throws IOException {
    String manifestString = "{\"app\":\"((appname))\",\"version\":\"((someversion))\"}";
    String varsString = "{\"appname\":\"bobservice1\",\"someversion\":\"v999\"}";
    InputStream manifestTemplate = new ByteArrayInputStream(manifestString.getBytes());
    InputStream vars = new ByteArrayInputStream(varsString.getBytes());

    CloudFoundryBakeManifestRequest request = new CloudFoundryBakeManifestRequest();
    request.setManifestTemplate(
        Artifact.builder()
            .reference(Base64.getEncoder().encodeToString(manifestString.getBytes()))
            .build());
    request.setVarsArtifacts(ImmutableList.of(Artifact.builder().build()));

    when(artifactDownloader.downloadArtifact(any())).thenReturn(manifestTemplate).thenReturn(vars);

    Artifact artifact = cloudFoundryBakeManifestService.bake(request);
    String resolvedManifest =
        new String(Base64.getDecoder().decode(artifact.getReference()), "UTF-8");
    String expectedManifest = "{\"app\":\"bobservice1\",\"version\":\"v999\"}";
    assertThat(resolvedManifest).asString().isEqualTo(expectedManifest);
  }

  @Test
  public void shouldResolveManifestWithMapVars() throws IOException {
    String manifestString = "{\"app\":\"((app.name))\",\"version\":\"((someversion))\"}";
    String varsString = "{\"app\":{\"name\":\"bobservice1\"},\"someversion\":\"v999\"}";
    InputStream manifestTemplate = new ByteArrayInputStream(manifestString.getBytes());
    InputStream vars = new ByteArrayInputStream(varsString.getBytes());

    CloudFoundryBakeManifestRequest request = new CloudFoundryBakeManifestRequest();
    request.setManifestTemplate(
        Artifact.builder()
            .reference(Base64.getEncoder().encodeToString(manifestString.getBytes()))
            .build());
    request.setVarsArtifacts(ImmutableList.of(Artifact.builder().build()));

    when(artifactDownloader.downloadArtifact(any())).thenReturn(manifestTemplate).thenReturn(vars);

    Artifact artifact = cloudFoundryBakeManifestService.bake(request);
    String resolvedManifest =
        new String(Base64.getDecoder().decode(artifact.getReference()), "UTF-8");
    String expectedManifest = "{\"app\":\"bobservice1\",\"version\":\"v999\"}";
    assertThat(resolvedManifest).asString().isEqualTo(expectedManifest);
  }

  @Test
  public void shouldResolveManifestWithListVars() throws IOException {
    String manifestString = "{\"app\":\"((app[0]))\",\"version\":\"((someversion))\"}";
    String varsString = "{\"app\":[\"bobservice1\"],\"someversion\":\"v999\"}";
    InputStream manifestTemplate = new ByteArrayInputStream(manifestString.getBytes());
    InputStream vars = new ByteArrayInputStream(varsString.getBytes());

    CloudFoundryBakeManifestRequest request = new CloudFoundryBakeManifestRequest();
    request.setManifestTemplate(
        Artifact.builder()
            .reference(Base64.getEncoder().encodeToString(manifestString.getBytes()))
            .build());
    request.setVarsArtifacts(ImmutableList.of(Artifact.builder().build()));

    when(artifactDownloader.downloadArtifact(any())).thenReturn(manifestTemplate).thenReturn(vars);

    Artifact artifact = cloudFoundryBakeManifestService.bake(request);
    String resolvedManifest =
        new String(Base64.getDecoder().decode(artifact.getReference()), "UTF-8");
    String expectedManifest = "{\"app\":\"bobservice1\",\"version\":\"v999\"}";
    assertThat(resolvedManifest).asString().isEqualTo(expectedManifest);
  }

  @Test
  public void shouldResolveManifestWithComplexVars() throws IOException {
    String manifestString = "{\"app\":\"((app[0].name))\",\"version\":\"((someversion))\"}";
    String varsString =
        "{\"app\":[{\"name\":\"bobservice1\",\"secondary\":\"tester\"}],\"someversion\":\"v999\"}";
    InputStream manifestTemplate = new ByteArrayInputStream(manifestString.getBytes());
    InputStream vars = new ByteArrayInputStream(varsString.getBytes());

    CloudFoundryBakeManifestRequest request = new CloudFoundryBakeManifestRequest();
    request.setManifestTemplate(
        Artifact.builder()
            .reference(Base64.getEncoder().encodeToString(manifestString.getBytes()))
            .build());
    request.setVarsArtifacts(ImmutableList.of(Artifact.builder().build()));

    when(artifactDownloader.downloadArtifact(any())).thenReturn(manifestTemplate).thenReturn(vars);

    Artifact artifact = cloudFoundryBakeManifestService.bake(request);
    String resolvedManifest =
        new String(Base64.getDecoder().decode(artifact.getReference()), "UTF-8");
    String expectedManifest = "{\"app\":\"bobservice1\",\"version\":\"v999\"}";
    assertThat(resolvedManifest).asString().isEqualTo(expectedManifest);
  }

  @Test
  public void shouldThrowWithUnknownKeys() throws IOException {
    String manifestString = "{\"app\":\"((appname1))\",\"version\":\"((someversion))\"}";
    String varsString = "{\"appname\":\"bobservice1\",\"someversion\":\"v999\"}";
    InputStream manifestTemplate = new ByteArrayInputStream(manifestString.getBytes());
    InputStream vars = new ByteArrayInputStream(varsString.getBytes());

    CloudFoundryBakeManifestRequest request = new CloudFoundryBakeManifestRequest();
    request.setManifestTemplate(
        Artifact.builder()
            .reference(Base64.getEncoder().encodeToString(manifestString.getBytes()))
            .build());
    request.setVarsArtifacts(ImmutableList.of(Artifact.builder().build()));

    when(artifactDownloader.downloadArtifact(any())).thenReturn(manifestTemplate).thenReturn(vars);

    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> cloudFoundryBakeManifestService.bake(request),
            "Expected it to throw an error but it didn't");
    assertThat(exception.getMessage())
        .isEqualTo("Unable to resolve values for the following keys: \n((appname1))");
  }
}
