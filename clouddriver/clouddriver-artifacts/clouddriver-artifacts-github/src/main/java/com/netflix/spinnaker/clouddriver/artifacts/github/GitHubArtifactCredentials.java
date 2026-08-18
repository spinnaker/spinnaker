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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.SimpleHttpArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.exceptions.FailedDownloadException;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import java.io.IOException;
import java.net.URI;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;

@NonnullByDefault
@Slf4j
public class GitHubArtifactCredentials extends SimpleHttpArtifactCredentials<GitHubArtifactAccount>
    implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-github";
  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of("github/file");

  @JsonIgnore private final ObjectMapper objectMapper;
  private final boolean useContentAPI;
  @JsonIgnore @Nullable private final GitHubAppAuthenticator gitHubAppAuthenticator;

  GitHubArtifactCredentials(
      GitHubArtifactAccount account, OkHttpClient okHttpClient, ObjectMapper objectMapper) {
    this(account, okHttpClient, objectMapper, null);
  }

  // visible for testing
  GitHubArtifactCredentials(
      GitHubArtifactAccount account,
      OkHttpClient okHttpClient,
      ObjectMapper objectMapper,
      @Nullable GitHubAppAuthenticator gitHubAppAuthenticator) {
    super(okHttpClient, account);
    this.name = account.getName();
    this.objectMapper = objectMapper;
    this.useContentAPI = account.isUseContentAPI();
    this.gitHubAppAuthenticator =
        account
            .getGithubApp()
            .map(
                githubApp ->
                    gitHubAppAuthenticator != null
                        ? gitHubAppAuthenticator
                        : githubApp.toAuthenticator())
            .orElse(null);
  }

  @Override
  protected Headers getHeaders(GitHubArtifactAccount account) throws IOException {
    Headers headers = super.getHeaders(account);
    if (gitHubAppAuthenticator != null) {
      // The installation token is resolved per request; the authenticator caches it and refreshes
      // it before expiration.
      headers =
          headers
              .newBuilder()
              .set("Authorization", "token " + gitHubAppAuthenticator.getInstallationToken())
              .build();
    }
    if (account.isUseContentAPI()) {
      return headers
          .newBuilder()
          .add(
              "Accept",
              String.format("application/vnd.github.%s.raw", account.getGithubAPIVersion()))
          .build();
    }
    return headers;
  }

  private HttpUrl getMetadataUrl(Artifact artifact) {
    String version = Strings.nullToEmpty(artifact.getVersion());
    if (version.isEmpty()) {
      log.info("No version specified for artifact {}, using 'master'.", version);
      version = "master";
    }

    HttpUrl metadataUrl =
        parseUrl(artifact.getReference()).newBuilder().addQueryParameter("ref", version).build();
    validateGitHubAppApiBaseUrlHost(metadataUrl);
    return metadataUrl;
  }

  /**
   * The installation token is minted against githubApp.apiBaseUrl but used against the artifact
   * reference's host, so both must belong to the same GitHub instance. The reference is already a
   * GitHub API URL (e.g. https://api.github.com/repos/... or https://ghe.example.com/api/v3/...),
   * so the hosts must match exactly.
   */
  private void validateGitHubAppApiBaseUrlHost(HttpUrl reference) {
    if (gitHubAppAuthenticator == null) {
      return;
    }
    String apiBaseUrl = getAccount().getGithubApp().orElseThrow().getApiBaseUrl();
    String apiHost = URI.create(apiBaseUrl).getHost();
    if (apiHost == null) {
      throw new IllegalArgumentException(
          "Unable to extract host from githubApp.apiBaseUrl: " + apiBaseUrl);
    }
    if (!reference.host().equalsIgnoreCase(apiHost)) {
      throw new IllegalArgumentException(
          String.format(
              "Artifact reference host '%s' does not match githubApp.apiBaseUrl host '%s' for github"
                  + " account '%s'. Set githubApp.apiBaseUrl to the GitHub API base URL of the"
                  + " instance hosting the repository (e.g. https://%s/api/v3 for GitHub Enterprise).",
              reference.host(), apiHost, getName(), reference.host()));
    }
  }

  @Override
  protected HttpUrl getDownloadUrl(Artifact artifact) throws IOException {
    if (this.useContentAPI) {
      return getMetadataUrl(artifact);
    }
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
    if (Strings.isNullOrEmpty(metadata.downloadUrl)) {
      throw new FailedDownloadException(
          "Failed to retrieve your github artifact's download URL. This is likely due to incorrect auth setup. Artifact: "
              + artifact);
    }
    return parseUrl(metadata.getDownloadUrl());
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }

  @Data
  static class ContentMetadata {
    @JsonProperty("download_url")
    @Nullable
    private String downloadUrl;
  }
}
