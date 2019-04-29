/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ArtifactExtractor {
  private List<WebhookArtifactExtractor> artifactExtractors = new ArrayList<>();

  @Autowired
  public ArtifactExtractor(List<WebhookArtifactExtractor> artifactExtractors) {
    this.artifactExtractors = artifactExtractors;
  }

  public List<Artifact> extractArtifacts(String type, String source, Map payload) {
    return artifactExtractors.stream()
        .filter(p -> p.handles(type, source))
        .map(e -> e.getArtifacts(source, payload))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }
}
