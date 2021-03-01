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
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  public GitRepoArtifactCredentials(GitJobExecutor executor, GitRepoFileSystem gitRepoFileSystem) {
    this.executor = executor;
    this.gitRepoFileSystem = gitRepoFileSystem;
    this.name = this.executor.getAccount().getName();
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String repoUrl = artifact.getReference();
    String subPath = artifactSubPath(artifact);
    String branch = artifactVersion(artifact);
    Path stagingPath = gitRepoFileSystem.getLocalClonePath(repoUrl, branch);
    String repoBasename = getRepoBasename(repoUrl);
    Path outputFile = Paths.get(stagingPath.toString(), repoBasename + ".tgz");

    if (!gitRepoFileSystem.canRetainClone()) {
      // delete clone before returning
      try (Closeable ignored = () -> FileUtils.deleteDirectory(stagingPath.toFile())) {
        return getInputStream(repoUrl, subPath, branch, stagingPath, repoBasename, outputFile);
      }
    } else {
      // clones are deleted by gitRepoFileSystem depending on retention period
      return getInputStream(repoUrl, subPath, branch, stagingPath, repoBasename, outputFile);
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
    try {
      if (gitRepoFileSystem.tryTimedLock(repoUrl, branch)) {
        try {
          executor.cloneOrPull(repoUrl, branch, stagingPath, repoBasename);
          log.info("Creating archive for git/repo {}", repoUrl);
          executor.archive(
              Paths.get(stagingPath.toString(), repoBasename), branch, subPath, outputFile);
          return new FileInputStream(outputFile.toFile());
        } finally {
          gitRepoFileSystem.unlock(repoUrl, branch);
        }
      } else {
        throw new IllegalStateException(
            "Timeout waiting to acquire file system lock for "
                + repoUrl
                + " (branch "
                + branch
                + ").");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Interrupted while waiting to acquire file system lock for "
              + repoUrl
              + " (branch "
              + branch
              + ").",
          e);
    }
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
