/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import jakarta.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

@NonnullByDefault
@Slf4j
public class GitRepoArtifactCredentials implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "git/repo";
  private static final Pattern GENERIC_URL_PATTERN = Pattern.compile("^.*/(.*)$");

  @Getter private final ImmutableList<String> types = ImmutableList.of("git/repo");
  @Getter private final String name;

  private final GitJobExecutor executor;
  private final GitRepoFileSystem gitRepoFileSystem;
  private final List<String> allowedHosts;

  public GitRepoArtifactCredentials(
      GitJobExecutor executor, GitRepoFileSystem gitRepoFileSystem, List<String> allowedHosts) {
    this.executor = executor;
    this.gitRepoFileSystem = gitRepoFileSystem;
    this.allowedHosts = allowedHosts;
    this.name = this.executor.getAccount().getName();
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String repoUrl = artifact.getReference();
    validateHostIsAlllowedHost(repoUrl);
    String subPath = artifactSubPath(artifact);
    String branch = artifactVersion(artifact);
    Path stagingPath = gitRepoFileSystem.getLocalClonePath(repoUrl, branch);
    String repoBasename = getRepoBasename(repoUrl);
    Path outputFile = Paths.get(stagingPath.toString(), repoBasename + ".tgz");

    try {
      return getLockedInputStream(repoUrl, subPath, branch, stagingPath, repoBasename, outputFile);
    } catch (InterruptedException e) {
      throw new IOException(
          "Interrupted while waiting to acquire file system lock for "
              + repoUrl
              + " (branch "
              + branch
              + ").",
          e);
    }
  }

  private void validateHostIsAlllowedHost(@Nullable String repoUrl)
      throws IllegalArgumentException {
    // If allowedHosts is null or empty, allow all hosts
    if (allowedHosts == null || allowedHosts.isEmpty()) {
      log.warn(
          "No allow hosts set on the account "
              + executor.getAccount().getName()
              + " - should set some limits on these accounts on which hosts it can connect to");
      return;
    }
    String hostname = extractHostname(repoUrl);

    // Check if hostname matches any allowed host (case-insensitive)
    // Supports both exact matches and subdomain matches
    boolean isAllowed =
        allowedHosts.stream()
            .anyMatch(
                allowedHost ->
                    allowedHost.equalsIgnoreCase(hostname)
                        || hostname.endsWith("." + allowedHost.toLowerCase()));

    if (!isAllowed) {
      throw new IllegalArgumentException(
          "Repository host '" + hostname + "' is not in the allowed hosts list: " + allowedHosts);
    }
  }

  private String extractHostname(@Nullable String repoUrl) {
    if (Strings.isNullOrEmpty(repoUrl)) {
      throw new IllegalArgumentException("Repository URL cannot be null or empty");
    }

    // Handle git@ style URLs (e.g., git@github.com:user/repo.git)
    if (repoUrl.startsWith("git@")) {
      int colonIndex = repoUrl.indexOf(':', 4);
      if (colonIndex > 0) {
        return repoUrl.substring(4, colonIndex).toLowerCase();
      }
      throw new IllegalArgumentException("Invalid git@ URL format: " + repoUrl);
    }

    // Handle standard URLs (http, https, git, ssh)
    try {
      java.net.URI uri = new java.net.URI(repoUrl);
      String host = uri.getHost();
      if (host != null) {
        return host.toLowerCase();
      }
    } catch (java.net.URISyntaxException e) {
      throw new IllegalArgumentException("Unable to parse repository URL: " + repoUrl, e);
    }

    throw new IllegalArgumentException("Unable to extract hostname from URL: " + repoUrl);
  }

  @NotNull
  private FileInputStream getLockedInputStream(
      String repoUrl,
      String subPath,
      String branch,
      Path stagingPath,
      String repoBasename,
      Path outputFile)
      throws InterruptedException, IOException {

    if (gitRepoFileSystem.tryTimedLock(repoUrl, branch)) {
      try {
        return getInputStream(repoUrl, subPath, branch, stagingPath, repoBasename, outputFile);

      } finally {
        // if not deleted explicitly, clones are deleted by
        // gitRepoFileSystem depending on retention period
        if (!gitRepoFileSystem.canRetainClone()) {
          log.debug("Deleting clone for {} (branch {})", repoUrl, branch);
          FileUtils.deleteDirectory(stagingPath.toFile());
        }
        gitRepoFileSystem.unlock(repoUrl, branch);
      }

    } else {
      throw new IOException(
          "Timeout waiting to acquire file system lock for "
              + repoUrl
              + " (branch "
              + branch
              + "). Waited "
              + gitRepoFileSystem.getCloneWaitLockTimeoutSec()
              + " seconds.");
    }
  }

  @NotNull
  private FileInputStream getInputStream(
      String repoUrl,
      String subPath,
      String branch,
      Path stagingPath,
      String repoBasename,
      Path outputFile)
      throws IOException {
    executor.cloneOrPull(repoUrl, branch, stagingPath, repoBasename);
    log.info("Creating archive for git/repo {}", repoUrl);
    executor.archive(Paths.get(stagingPath.toString(), repoBasename), branch, subPath, outputFile);
    return new FileInputStream(outputFile.toFile());
  }

  private String getRepoBasename(String url) {
    Matcher matcher = GENERIC_URL_PATTERN.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Git repo url " + url + " doesn't match regex " + GENERIC_URL_PATTERN);
    }
    return matcher.group(1).replaceAll("\\.git$", "");
  }

  private String artifactSubPath(Artifact artifact) {
    if (!Strings.nullToEmpty(artifact.getLocation()).isEmpty()) {
      return artifact.getLocation();
    }
    return Strings.nullToEmpty((String) artifact.getMetadata("subPath"));
  }

  private String artifactVersion(Artifact artifact) {
    return !Strings.isNullOrEmpty(artifact.getVersion()) ? artifact.getVersion() : "master";
  }
}
