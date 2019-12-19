/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.igor.config.ManagedDeliveryConfigProperties;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster;
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Support for retrieving Managed Delivery-related information from SCM systems. */
@Service
@EnableConfigurationProperties(ManagedDeliveryConfigProperties.class)
public class ManagedDeliveryScmService {
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);

  private final ManagedDeliveryConfigProperties configProperties;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;
  private final Optional<StashMaster> stashMaster;
  private final Optional<GitHubMaster> gitHubMaster;
  private final Optional<GitLabMaster> gitLabMaster;
  private final Optional<BitBucketMaster> bitBucketMaster;

  public ManagedDeliveryScmService(
      Optional<ManagedDeliveryConfigProperties> configProperties,
      Optional<StashMaster> stashMaster,
      Optional<GitHubMaster> gitHubMaster,
      Optional<GitLabMaster> gitLabMaster,
      Optional<BitBucketMaster> bitBucketMaster,
      ObjectMapper jsonMapper) {
    this.configProperties =
        configProperties.isPresent()
            ? configProperties.get()
            : new ManagedDeliveryConfigProperties();
    this.stashMaster = stashMaster;
    this.gitHubMaster = gitHubMaster;
    this.gitLabMaster = gitLabMaster;
    this.bitBucketMaster = bitBucketMaster;
    this.jsonMapper = jsonMapper;
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  /**
   * Given details about a supported git source control repository, and optional filters for a
   * sub-directory within the repo, the file extensions to look for, and the specific git reference
   * to use, returns a list of (potential) Managed Delivery config manifests found at that location.
   *
   * <p>Note that this method does not recurse the specified sub-directory when listing files.
   */
  public List<String> listDeliveryConfigManifests(
      final String scmType,
      final String project,
      final String repository,
      final String directory,
      final String extension,
      final String ref) {

    if (scmType == null || project == null || repository == null) {
      throw new IllegalArgumentException("scmType, project and repository are required arguments");
    }

    final String path =
        configProperties.getManifestBasePath() + "/" + ((directory != null) ? directory : "");

    log.debug(
        "Listing keel manifests at " + scmType + "://" + project + "/" + repository + "/" + path);

    return getScmMaster(scmType)
        .listDirectory(project, repository, path, (ref != null) ? ref : ScmMaster.DEFAULT_GIT_REF)
        .stream()
        .filter(it -> it.endsWith("." + ((extension != null) ? extension : "yml")))
        .collect(Collectors.toList());
  }

  /**
   * Given details about a supported git source control repository, the filename of a Managed
   * Delivery config manifest, and optional filters for a sub-directory within the repo and the
   * specific git reference to use, returns the contents of the manifest.
   *
   * <p>This API supports both YAML and JSON for the format of the manifest in source control, but
   * always returns the parsed contents as a Map.
   */
  public Map<String, Object> getDeliveryConfigManifest(
      final String scmType,
      final String project,
      final String repository,
      final String directory,
      final String manifest,
      final String ref) {

    if (scmType == null || project == null || repository == null) {
      throw new IllegalArgumentException("scmType, project and repository are required arguments");
    }

    if (!(manifest.endsWith(".yml") || manifest.endsWith(".yaml") || manifest.endsWith(".json"))) {
      throw new IllegalArgumentException(
          String.format("Unrecognized file format for %s. Please use YAML or JSON.", manifest));
    }

    final String path =
        (configProperties.getManifestBasePath()
            + "/"
            + ((directory != null) ? directory + "/" : "")
            + manifest);

    log.debug(
        "Retrieving delivery config manifest from " + project + ":" + repository + "/" + path);
    String manifestContents =
        getScmMaster(scmType).getTextFileContents(project, repository, path, ref);

    try {
      if (manifest.endsWith(".json")) {
        return (Map<String, Object>) jsonMapper.readValue(manifestContents, Map.class);
      } else {
        return (Map<String, Object>) yamlMapper.readValue(manifestContents, Map.class);
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          String.format(
              "Error parsing contents of delivery config manifest %s: %s",
              manifest, e.getMessage()));
    }
  }

  private ScmMaster getScmMaster(final String scmType) {
    Optional<? extends ScmMaster> scmMaster;

    if (scmType.equalsIgnoreCase("bitbucket")) {
      scmMaster = bitBucketMaster;
    } else if (scmType.equalsIgnoreCase("github")) {
      scmMaster = gitHubMaster;
    } else if (scmType.equalsIgnoreCase("gitlab")) {
      scmMaster = gitLabMaster;
    } else if (scmType.equalsIgnoreCase("stash")) {
      scmMaster = stashMaster;
    } else {
      throw new IllegalArgumentException("Unknown SCM type " + scmType);
    }

    if (scmMaster.isPresent()) {
      return scmMaster.get();
    } else {
      throw new IllegalArgumentException(scmType + " client requested but not configured");
    }
  }
}
