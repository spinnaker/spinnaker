/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.echo.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class NexusArtifactExtractor implements WebhookArtifactExtractor {
  private final ObjectMapper mapper = EchoObjectMapper.getInstance();

  @Override
  public List<Artifact> getArtifacts(String source, Map payloadMap) {
    NexusPayload payload = mapper.convertValue(payloadMap, NexusPayload.class);
    if (payload.getComponent() == null) {
      return Collections.emptyList();
    } else {
      Component component = payload.getComponent();
      return Collections.singletonList(
          Artifact.builder()
              .type("maven/file")
              .name(component.getGroup() + ":" + component.getName())
              .reference(
                  component.getGroup() + ":" + component.getName() + ":" + component.getVersion())
              .version(component.getVersion())
              .provenance(payload.getRepositoryName())
              .build());
    }
  }

  @Override
  public boolean handles(String type, String source) {
    return "nexus".equalsIgnoreCase(source);
  }

  @Data
  private static class Component {
    private String group;
    private String name;
    private String version;
  }

  @Data
  private static class NexusPayload {
    private String repositoryName;
    private NexusArtifactExtractor.Component component;
  }
}
