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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.Manifest;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class CloudFoundryManifestArtifactDecorator implements CloneDescriptionDecorator {
  private final ObjectMapper mapper;
  private final ArtifactResolver artifactResolver;

  @Override
  public boolean shouldDecorate(Map<String, Object> operation) {
    return "cloudfoundry".equals(getCloudProvider(operation));
  }

  @Override
  public void decorate(Map<String, Object> operation, List<Map<String, Object>> descriptions, Stage stage) {
    CloudFoundryCloneServerGroupOperation op = mapper.convertValue(operation, CloudFoundryCloneServerGroupOperation.class);

    operation.put("applicationArtifact", Artifact.builder()
      .type("cloudfoundry/app")
      .artifactAccount(op.getSource().getAccount())
      .location(op.getSource().getRegion())
      .name(op.getSource().getAsgName())
      .build());
    operation.put("manifest", op.getManifest().toArtifact(artifactResolver, stage));
    operation.put("credentials", op.getDestination().getAccount());
    operation.put("region", op.getDestination().getRegion());

    operation.remove("source");
    operation.remove("destination");
  }

  @Data
  private static class CloudFoundryCloneServerGroupOperation {
    private Manifest manifest;
    private Source source;
    private Destination destination;

    @Data
    static class Source {
      String account;
      String region;
      String asgName;
    }

    @Data
    static class Destination {
      String account;
      String region;
    }
  }
}