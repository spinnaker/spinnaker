/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests.kustomize;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.manifests.kustomize.mapping.Kustomization;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import retrofit.client.Response;

@Component
@Slf4j
public class KustomizationFileReader {
  private final ClouddriverService clouddriverService;
  private final RetrySupport retrySupport = new RetrySupport();
  private static final List<String> KUSTOMIZATION_FILENAMES =
      ImmutableList.of("kustomization.yaml", "kustomization.yml", "kustomization");

  public KustomizationFileReader(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  public Kustomization getKustomization(Artifact artifact, String possibleName) {
    Path artifactPath = Paths.get(artifact.getReference());
    // sort list of names, trying the possibleName first.
    List<String> names =
        KUSTOMIZATION_FILENAMES.stream()
            .sorted(
                (a, b) ->
                    a.equals(possibleName) ? -1 : (b.equals(possibleName) ? 1 : a.compareTo(b)))
            .collect(Collectors.toList());

    Kustomization k = null;
    for (String name : names) {
      try {
        String artifactReference = new URI(artifact.getReference() + "/").resolve(name).toString();
        Artifact testArtifact = artifactFromBase(artifact, artifactReference);
        k = convert(testArtifact);
        k.setSelfReference(artifactReference);
        break;
      } catch (Exception e) {
        log.error(
            "kustomization file {} cannot be found at location", name, artifact.getReference());
      }
    }

    if (k == null) {
      throw new IllegalArgumentException(
          "Unable to find any kustomization file for " + artifact.getName());
    }

    return k;
  }

  private Artifact artifactFromBase(Artifact artifact, String path) {
    return Artifact.builder()
        .reference(path)
        .artifactAccount(artifact.getArtifactAccount())
        .type(artifact.getType())
        .build();
  }

  private Kustomization convert(Artifact artifact) throws IOException {
    Representer representer = new Representer();
    representer.getPropertyUtils().setSkipMissingProperties(true);
    return new Yaml(new Constructor(Kustomization.class), representer).load(downloadFile(artifact));
  }

  private InputStream downloadFile(Artifact artifact) throws IOException {
    log.info("downloading kustomization file {}", artifact.getReference());
    Response response =
        retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
    return response.getBody().in();
  }
}
