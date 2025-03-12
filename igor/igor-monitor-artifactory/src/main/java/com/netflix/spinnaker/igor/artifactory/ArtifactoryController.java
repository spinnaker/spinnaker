/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.artifactory;

import com.netflix.spinnaker.igor.artifactory.model.ArtifactorySearch;
import com.netflix.spinnaker.igor.config.ArtifactoryProperties;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty("artifactory.enabled")
@RequestMapping("/artifactory")
public class ArtifactoryController {

  private ArtifactoryProperties artifactoryProperties;

  public ArtifactoryController(ArtifactoryProperties artifactoryProperties) {
    this.artifactoryProperties = artifactoryProperties;
  }

  @GetMapping("/names")
  List<String> getArtifactoryNames() {
    return artifactoryProperties.getSearches().stream()
        .map(ArtifactorySearch::getName)
        .collect(Collectors.toList());
  }
}
