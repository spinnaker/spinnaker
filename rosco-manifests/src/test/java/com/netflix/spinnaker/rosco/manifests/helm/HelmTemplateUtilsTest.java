/*
 * Copyright 2019 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.rosco.manifests.helm;

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.manifests.ArtifactDownloader;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class HelmTemplateUtilsTest {
  @Test
  public void nullReferenceTest() throws IOException {
    ArtifactDownloader artifactDownloader = mock(ArtifactDownloader.class);
    HelmTemplateUtils helmTemplateUtils = new HelmTemplateUtils(artifactDownloader);
    Artifact chartArtifact = Artifact.builder().name("test-artifact").version("3").build();

    HelmBakeManifestRequest bakeManifestRequest = new HelmBakeManifestRequest();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact));
    bakeManifestRequest.setOverrides(ImmutableMap.of());

    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);
    }
  }
}
