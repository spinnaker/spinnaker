/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.igor.artifacts;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for supplying version options and artifacts for a trigger that triggers from artifact
 * events.
 */
@RestController
@RequestMapping("/artifacts")
public class ArtifactController {

  private final ArtifactServices artifactServices;

  @Autowired
  public ArtifactController(ArtifactServices artifactServices) {
    this.artifactServices = artifactServices;
  }

  @GetMapping("/{provider}/{name}")
  public List<String> getVersions(
      @PathVariable("provider") String provider, @PathVariable("name") String name) {
    ArtifactService artifactService = getService(provider);
    return artifactService.getArtifactVersions(name);
  }

  @GetMapping("/{provider}/{name}/{version:.+}")
  public Artifact getVersions(
      @PathVariable("provider") String provider,
      @PathVariable("name") String name,
      @PathVariable("version") String version) {
    ArtifactService artifactService = getService(provider);
    return artifactService.getArtifact(name, version);
  }

  private ArtifactService getService(String serviceName) {
    ArtifactService artifactService = artifactServices.getService(serviceName);
    if (artifactService == null) {
      throw new NotFoundException("Provider " + serviceName + " not found");
    }
    return artifactService;
  }
}
