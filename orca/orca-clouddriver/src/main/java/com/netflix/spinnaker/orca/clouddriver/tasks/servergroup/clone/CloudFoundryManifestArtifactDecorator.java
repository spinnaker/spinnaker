/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.clone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.DeploymentManifest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CloudFoundryManifestArtifactDecorator implements CloneDescriptionDecorator {
  private final ObjectMapper mapper;
  private final ManifestEvaluator manifestEvaluator;

  @Override
  public boolean shouldDecorate(Map<String, Object> operation) {
    return "cloudfoundry".equals(getCloudProvider(operation));
  }

  @Override
  public void decorate(
      Map<String, Object> operation, List<Map<String, Object>> descriptions, StageExecution stage) {
    CloudFoundryCloneServerGroupOperation op =
        mapper.convertValue(operation, CloudFoundryCloneServerGroupOperation.class);
    DeploymentManifest manifest =
        mapper.convertValue(stage.getContext().get("manifest"), DeploymentManifest.class);
    CloudFoundryManifestContext manifestContext =
        CloudFoundryManifestContext.builder()
            .source(ManifestContext.Source.Artifact)
            .manifestArtifactId(manifest.getArtifactId())
            .manifestArtifact(manifest.getArtifact())
            .manifestArtifactAccount(manifest.getArtifact().getArtifactAccount())
            .skipExpressionEvaluation(
                (Boolean)
                    Optional.ofNullable(stage.getContext().get("skipExpressionEvaluation"))
                        .orElse(false))
            .build();
    ManifestEvaluator.Result manifestResult = manifestEvaluator.evaluate(stage, manifestContext);
    operation.put(
        "applicationArtifact",
        Artifact.builder()
            .type("cloudfoundry/app")
            .artifactAccount(op.getSource().getAccount())
            .location(op.getSource().getRegion())
            .name(op.getSource().getAsgName())
            .build());
    operation.put("manifest", manifestResult.getManifests());
    operation.put("credentials", Optional.ofNullable(op.getAccount()).orElse(op.getCredentials()));
    operation.put("region", op.getRegion());

    operation.remove("source");
  }

  @Data
  private static class CloudFoundryCloneServerGroupOperation {
    private String account;
    private String credentials;
    private String region;
    private DeploymentManifest manifest;
    private Source source;

    @Data
    static class Source {
      String account;
      String region;
      String asgName;
    }
  }
}
