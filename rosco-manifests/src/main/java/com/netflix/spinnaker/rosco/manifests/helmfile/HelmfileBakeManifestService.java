/*
 * Copyright 2023 Grab Holdings, Inc.
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

package com.netflix.spinnaker.rosco.manifests.helmfile;

import static com.netflix.spinnaker.rosco.manifests.BakeManifestRequest.TemplateRenderer;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import java.io.IOException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class HelmfileBakeManifestService extends BakeManifestService<HelmfileBakeManifestRequest> {
  private final HelmfileTemplateUtils helmfileTemplateUtils;
  private static final ImmutableSet<String> supportedTemplates =
      ImmutableSet.of(TemplateRenderer.HELMFILE.toString());

  public HelmfileBakeManifestService(
      HelmfileTemplateUtils helmTemplateUtils, JobExecutor jobExecutor) {
    super(jobExecutor);
    this.helmfileTemplateUtils = helmTemplateUtils;
  }

  @Override
  public Class<HelmfileBakeManifestRequest> requestType() {
    return HelmfileBakeManifestRequest.class;
  }

  @Override
  public boolean handles(String type) {
    return supportedTemplates.contains(type);
  }

  public Artifact bake(HelmfileBakeManifestRequest helmfileBakeManifestRequest) throws IOException {
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmfileTemplateUtils.buildBakeRecipe(env, helmfileBakeManifestRequest);

      String bakeResult = helmfileTemplateUtils.removeTestsDirectoryTemplates(doBake(recipe));
      return Artifact.builder()
          .type("embedded/base64")
          .name(helmfileBakeManifestRequest.getOutputArtifactName())
          .reference(Base64.getEncoder().encodeToString(bakeResult.getBytes()))
          .build();
    }
  }
}
