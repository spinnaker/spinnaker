/*
 * Copyright 2017 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.github;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.SimpleHttpArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.exceptions.FailedDownloadException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.ResponseBody;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class GitHubArtifactCredentials extends SimpleHttpArtifactCredentials<GitHubArtifactAccount>
    implements ArtifactCredentials {
  @Getter private final String name;
  @Getter private final List<String> types = Collections.singletonList("github/file");

  @JsonIgnore private final ObjectMapper objectMapper;

  GitHubArtifactCredentials(
      GitHubArtifactAccount account, OkHttpClient okHttpClient, ObjectMapper objectMapper) {
    super(okHttpClient, account);
    this.name = account.getName();
    this.objectMapper = objectMapper;
  }

  private HttpUrl getMetadataUrl(Artifact artifact) {
    String version = artifact.getVersion();
    if (StringUtils.isEmpty(version)) {
      log.info("No version specified for artifact {}, using 'master'.", version);
      version = "master";
    }

    return parseUrl(artifact.getReference()).newBuilder().addQueryParameter("ref", version).build();
  }

  @Override
  protected HttpUrl getDownloadUrl(Artifact artifact) throws IOException {
    ResponseBody metadataResponse;
    try {
      metadataResponse = fetchUrl(getMetadataUrl(artifact));
    } catch (IOException e) {
      throw new FailedDownloadException(
          "Unable to determine the download URL of artifact " + artifact + ": " + e.getMessage(),
          e);
    }

    ContentMetadata metadata =
        objectMapper.readValue(metadataResponse.string(), ContentMetadata.class);
    if (StringUtils.isEmpty(metadata.downloadUrl)) {
      throw new FailedDownloadException(
          "Failed to retrieve your github artifact's download URL. This is likely due to incorrect auth setup. Artifact: "
              + artifact);
    }
    return parseUrl(metadata.getDownloadUrl());
  }

  @Data
  static class ContentMetadata {
    @JsonProperty("download_url")
    private String downloadUrl;
  }
}
