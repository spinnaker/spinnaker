/*
 * Copyright 2021 Armory
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class GitRepoFileSystem {
  private static final Path CLONES_HOME =
      Paths.get(System.getProperty("java.io.tmpdir"), "gitrepos");

  private final GitRepoArtifactProviderProperties config;
  private final Map<String, Lock> pathLocks = new ConcurrentHashMap<>();

  public GitRepoFileSystem(GitRepoArtifactProviderProperties properties) {
    this.config = properties;
  }

  public Path getLocalClonePath(String repoUrl, String branch) {
    return Paths.get(CLONES_HOME.toString(), hashCoordinates(repoUrl, branch));
  }

  public boolean tryTimedLock(String repoUrl, String branch) throws InterruptedException {
    String hash = hashCoordinates(repoUrl, branch);
    Lock lock = pathLocks.computeIfAbsent(hash, k -> new ReentrantLock());
    return lock.tryLock(config.getCloneWaitLockTimeoutSec(), TimeUnit.SECONDS);
  }

  public boolean tryLock(String cloneHashDir) {
    Lock lock = pathLocks.computeIfAbsent(cloneHashDir, k -> new ReentrantLock());
    return lock.tryLock();
  }

  public void unlock(String repoUrl, String branch) {
    unlock(hashCoordinates(repoUrl, branch));
  }

  public void unlock(String cloneHashDir) {
    if (!pathLocks.containsKey(cloneHashDir)) {
      return;
    }
    Lock lock = pathLocks.remove(cloneHashDir);
    lock.unlock();
  }

  public boolean canRetainClone() {
    return config.getCloneRetentionMinutes() != 0 && hasFreeDisk();
  }

  private boolean hasFreeDisk() {
    long currentSize = FileUtils.sizeOfDirectory(CLONES_HOME.toFile());
    return currentSize >= 0 && currentSize < config.getCloneRetentionMaxBytes();
  }

  private String hashCoordinates(String repoUrl, String branch) {
    String coordinates =
        String.format(
            "%s-%s",
            Optional.ofNullable(repoUrl).orElse("unknownUrl"),
            Optional.ofNullable(branch).orElse("defaultBranch"));
    return Hashing.sha256().hashString(coordinates, Charset.defaultCharset()).toString();
  }

  @Scheduled(
      fixedDelayString =
          "${artifacts.git-repo.clone-retention-check-ms:"
              + GitRepoArtifactProviderProperties.DEFAULT_CLONE_RETENTION_CHECK_MS
              + "}")
  private void deleteExpiredRepos() {
    try {
      if (!CLONES_HOME.toFile().exists() || config.getCloneRetentionMinutes() < 0) {
        return;
      }
      File[] repos = CLONES_HOME.toFile().listFiles();
      if (repos == null) {
        return;
      }
      for (File r : repos) {
        long ageMin = ((System.currentTimeMillis() - r.lastModified()) / 1000) / 60;
        if (ageMin < config.getCloneRetentionMinutes()) {
          continue;
        }
        if (!tryLock(r.getName())) {
          // move on if the directory is locked by another thread, just wait for the next cycle
          continue;
        }
        try {
          log.info("Deleting expired git clone {}", r.getName());
          FileUtils.forceDelete(r);
        } finally {
          unlock(r.getName());
        }
      }
    } catch (IOException e) {
      log.error("Error deleting expired git clones, ignoring", e);
    }
  }
}
