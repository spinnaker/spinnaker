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
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.github.GitHubAppAuthenticator;
import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import com.netflix.spinnaker.kork.github.GitHubRepoRef;
import java.io.IOException;
import java.util.Optional;
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

  /** Both non-null exactly when the account is configured for GitHub App authentication. */
  @JsonIgnore @Nullable private final GitHubAppCredentials githubApp;

  @JsonIgnore @Nullable private final GitHubAppAuthenticator gitHubAppAuthenticator;

  GitHubArtifactCredentials(
      GitHubArtifactAccount account, OkHttpClient okHttpClient, ObjectMapper objectMapper)
      throws IOException {
    this(account, okHttpClient, objectMapper, null);
  }

  @VisibleForTesting
  GitHubArtifactCredentials(
      GitHubArtifactAccount account,
      OkHttpClient okHttpClient,
      ObjectMapper objectMapper,
      @Nullable GitHubAppAuthenticator gitHubAppAuthenticator)
      throws IOException {
    super(okHttpClient, account);
    this.name = account.getName();
    this.objectMapper = objectMapper;
    this.useContentAPI = account.isUseContentAPI();
    this.githubApp = account.getGithubApp().orElse(null);
    if (githubApp == null) {
      this.gitHubAppAuthenticator = null;
    } else {
      this.gitHubAppAuthenticator =
          gitHubAppAuthenticator != null
              ? gitHubAppAuthenticator
              : githubApp.toAuthenticator("github artifact account '" + account.getName() + "'");
    }
  }

  @Override
  protected Headers getHeaders(GitHubArtifactAccount account, HttpUrl url) throws IOException {
    Headers headers = super.getHeaders(account);
    if (gitHubAppAuthenticator != null) {
      // The installation token is resolved per request; the authenticator serves cached tokens
      // without contacting the GitHub API and refreshes them before expiration.
      Optional<String> installationToken = installationTokenFor(url);
      if (installationToken.isPresent()) {
        headers =
            headers.newBuilder().set("Authorization", "token " + installationToken.get()).build();
      }
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

  /**
   * Resolves the installation token for the URL being fetched. In pinned mode (appInstallationId
   * configured) the configured installation is used; otherwise the installation is derived from the
   * repository the URL points at.
   *
   * <p>Returns empty when the repository cannot be determined from the URL, which happens for raw
   * content download URLs whose layout differs between github.com and GitHub Enterprise. Those URLs
   * carry their own access token when the contents API issues them, so the request is sent without
   * GitHub App credentials rather than authenticated as a guessed repository.
   */
  private Optional<String> installationTokenFor(HttpUrl url) throws IOException {
    if (githubApp.hasInstallationId()) {
      return Optional.of(gitHubAppAuthenticator.getInstallationToken());
    }
    Optional<GitHubRepoRef> repoRef = GitHubRepoRef.fromPathSegments(url.pathSegments());
    if (repoRef.isEmpty()) {
      log.debug(
          "Unable to determine the repository from {} for github artifact account {}; sending the request without GitHub App credentials. Set githubApp.appInstallationId to pin an installation.",
          url,
          name);
      return Optional.empty();
    }
    return Optional.of(
        gitHubAppAuthenticator.getInstallationTokenForRepo(
            repoRef.get().getOwner(), repoRef.get().getRepo()));
  }

  private HttpUrl getMetadataUrl(Artifact artifact) {
    String version = Strings.nullToEmpty(artifact.getVersion());
    if (version.isEmpty()) {
      log.info("No version specified for artifact {}, using 'master'.", version);
      version = "master";
    }

    return parseUrl(artifact.getReference()).newBuilder().addQueryParameter("ref", version).build();
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
