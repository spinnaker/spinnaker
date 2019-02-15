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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
@Data
public class GitHubArtifactCredentials implements ArtifactCredentials {
  private final String name;
  private final List<String> types = Collections.singletonList("github/file");

  @JsonIgnore
  private final Headers headers;

  @JsonIgnore
  OkHttpClient okHttpClient;

  @JsonIgnore
  ObjectMapper objectMapper;

  public GitHubArtifactCredentials(GitHubArtifactAccount account, OkHttpClient okHttpClient, ObjectMapper objectMapper) {
    this.name = account.getName();
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
    this.headers = getHeaders(account);
  }

  private Request.Builder requestBuilder() {
    return new Request.Builder().headers(headers);
  }

  private Headers getHeaders(GitHubArtifactAccount account) {
    Headers.Builder headers = new Headers.Builder();
    String authHeader = getAuthHeader(account);
    if (authHeader != null) {
      headers.set("Authorization", authHeader);
      log.info("Loaded credentials for GitHub Artifact Account {}", account.getName());
    } else {
      log.info("No credentials included with GitHub Artifact Account {}", account.getName());
    }
    return headers.build();
  }

  private String getAuthHeader(GitHubArtifactAccount account) {
    if (StringUtils.isNotEmpty(account.getTokenFile())) {
      return "token " + credentialsFromFile(account.getTokenFile());
    }

    if (StringUtils.isNotEmpty(account.getUsernamePasswordFile())) {
      return "Basic " + Base64.encodeBase64String(credentialsFromFile(account.getUsernamePasswordFile()).getBytes());
    }

    if (StringUtils.isNotEmpty(account.getToken())) {
      return "token " + account.getToken();
    }

    if (StringUtils.isNotEmpty(account.getUsername()) && StringUtils.isNotEmpty(account.getPassword())) {
      return "Basic " + Base64.encodeBase64String((account.getUsername() + ":" + account.getPassword()).getBytes());
    }

    return null;
  }

  private String credentialsFromFile(String filename) {
    try {
      String credentials = FileUtils.readFileToString(new File(filename));
      return credentials.replace("\n", "");
    } catch (IOException e) {
      log.error("Could not read GitHub credentials file {}", filename, e);
      return null;
    }
  }

  public InputStream download(Artifact artifact) throws IOException {
    HttpUrl.Builder metadataUrlBuilder;
    try {
      metadataUrlBuilder = HttpUrl.parse(artifact.getReference()).newBuilder();
    } catch (Exception e) {
      throw new IllegalArgumentException("Malformed github content URL in 'reference'. Read more here https://www.spinnaker.io/reference/artifacts/types/github-file/: " + e.getMessage(), e);
    }
    String version = artifact.getVersion();
    if (StringUtils.isEmpty(version)) {
      log.info("No version specified for artifact {}, using 'master'.", version);
      version = "master";
    }

    metadataUrlBuilder.addQueryParameter("ref", version);
    Request metadataRequest = requestBuilder()
      .url(metadataUrlBuilder.build().toString())
      .build();

    Response metadataResponse;
    try {
      metadataResponse = okHttpClient.newCall(metadataRequest).execute();
    } catch (IOException e) {
      throw new FailedDownloadException("Unable to determine the download URL of artifact " + artifact + ": " + e.getMessage(), e);
    }

    String body = metadataResponse.body().string();
    ContentMetadata metadata = objectMapper.readValue(body, ContentMetadata.class);
    if (StringUtils.isEmpty(metadata.downloadUrl)) {
      throw new FailedDownloadException("Failed to retrieve your github artifact's download URL. This is likely due to incorrect auth setup. Artifact: " + artifact);
    }

    Request downloadRequest = requestBuilder()
      .url(metadata.getDownloadUrl())
      .build();

    try {
      Response downloadResponse = okHttpClient.newCall(downloadRequest).execute();
      return downloadResponse.body().byteStream();
    } catch (IOException e) {
      throw new FailedDownloadException("Unable to download the contents of artifact " + artifact + ": " + e.getMessage(), e);
    }
  }

  @Data
  public static class ContentMetadata {
    @JsonProperty("download_url")
    private String downloadUrl;
  }

  public class FailedDownloadException extends IOException {
    public FailedDownloadException(String message) {
      super(message);
    }

    public FailedDownloadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
