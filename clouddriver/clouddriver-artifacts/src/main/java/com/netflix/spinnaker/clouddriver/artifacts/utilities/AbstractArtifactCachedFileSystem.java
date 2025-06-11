/*
 * Copyright 2025 Harness, Inc
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

package com.netflix.spinnaker.clouddriver.artifacts.utilities;

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

/**
 * Abstract class that provides common functionality for artifact file systems. This includes
 * locking mechanisms, file path management, and retention functionality.
 */
@Slf4j
public abstract class AbstractArtifactCachedFileSystem {

  protected final Path clonesHome;
  protected final Map<String, Lock> pathLocks = new ConcurrentHashMap<>();

  /**
   * Constructor that initializes the file system with a specific clones home directory.
   *
   * @param clonesDirectoryName The name of the directory where clones will be stored
   */
  protected AbstractArtifactCachedFileSystem(String clonesDirectoryName) {
    this.clonesHome = Paths.get(System.getProperty("java.io.tmpdir"), clonesDirectoryName);
  }

  /**
   * Gets the local clone path for an artifact.
   *
   * @param artifactName The name or URL of the artifact
   * @param artifactVersion The version, branch, or tag of the artifact
   * @return The path where the artifact is or will be cloned
   */
  public Path getLocalClonePath(String artifactName, String artifactVersion) {
    log.debug(
        "Getting local clone path for {} (version {}), hash: {} in path {}",
        artifactName,
        artifactVersion,
        hashCoordinates(artifactName, artifactVersion),
        clonesHome.toString());
    return Paths.get(clonesHome.toString(), hashCoordinates(artifactName, artifactVersion));
  }

  /**
   * Gets the timeout for waiting to acquire a lock.
   *
   * @return The timeout in seconds
   */
  public abstract int getCloneWaitLockTimeoutSec();

  /**
   * Tries to acquire a lock with a timeout.
   *
   * @param artifactName The name or URL of the artifact
   * @param artifactVersion The version, branch, or tag of the artifact
   * @return true if the lock was acquired, false otherwise
   * @throws InterruptedException if the thread is interrupted while waiting for the lock
   */
  public boolean tryTimedLock(String artifactName, String artifactVersion)
      throws InterruptedException {
    String hash = hashCoordinates(artifactName, artifactVersion);

    log.debug(
        "Trying filesystem timed lock for {} (version {}), hash: {} for {} seconds",
        artifactName,
        artifactVersion,
        hash,
        getCloneWaitLockTimeoutSec());

    Lock lock = createOrGetLock(hash);
    boolean locked = lock.tryLock(getCloneWaitLockTimeoutSec(), TimeUnit.SECONDS);
    log.debug(
        "Lock {} acquired for {} (version {}), hash {}, lock instance: {}",
        (locked ? "" : "NOT"),
        artifactName,
        artifactVersion,
        hash,
        lock);
    return locked;
  }

  /**
   * Creates or gets a lock for a specific hash.
   *
   * @param hash The hash to get or create a lock for
   * @return The lock
   */
  protected synchronized Lock createOrGetLock(String hash) {
    if (!pathLocks.containsKey(hash)) {
      log.info("Creating new lock instance for hash: {}", hash);
      pathLocks.put(hash, new ReentrantLock());
    }
    return pathLocks.get(hash);
  }

  /**
   * Tries to acquire a lock without a timeout.
   *
   * @param cloneHashDir The hash directory to lock
   * @return true if the lock was acquired, false otherwise
   */
  public boolean tryLock(String cloneHashDir) {
    log.info("Trying filesystem lock for hash: {}", cloneHashDir);
    Lock lock = createOrGetLock(cloneHashDir);
    boolean locked = lock.tryLock();
    log.info("Lock {} acquired for hash {}", (locked ? "" : "NOT"), cloneHashDir);
    return locked;
  }

  /**
   * Unlocks a specific artifact.
   *
   * @param artifactName The name or URL of the artifact
   * @param artifactVersion The version, branch, or tag of the artifact
   */
  public void unlock(String artifactName, String artifactVersion) {
    String hash = hashCoordinates(artifactName, artifactVersion);
    log.debug(
        "Unlocking filesystem for {} (version {}), hash: {}", artifactName, artifactVersion, hash);
    unlock(hash);
  }

  /**
   * Unlocks a specific hash directory.
   *
   * @param cloneHashDir The hash directory to unlock
   */
  public synchronized void unlock(String cloneHashDir) {
    if (!pathLocks.containsKey(cloneHashDir)) {
      log.warn(
          "Attempting to unlock filesystem with hash {} that doesn't have a lock", cloneHashDir);
      return;
    }
    Lock lock = pathLocks.get(cloneHashDir);
    log.debug("Unlocking filesystem for hash {}, lock instance: {}", cloneHashDir, lock);
    lock.unlock();
  }

  /**
   * Determines if a clone can be retained.
   *
   * @return true if the clone can be retained, false otherwise
   */
  public abstract boolean canRetainClone();

  /**
   * Determines if there is enough free disk space.
   *
   * @return true if there is enough free disk space, false otherwise
   */
  protected boolean hasFreeDisk() {
    long currentSize = 0;
    if (clonesHome.toFile().exists()) {
      currentSize = FileUtils.sizeOfDirectory(clonesHome.toFile());
    }
    return currentSize >= 0 && currentSize < getCloneRetentionMaxBytes();
  }

  /**
   * Gets the maximum number of bytes that can be used for clones.
   *
   * @return The maximum number of bytes
   */
  protected abstract long getCloneRetentionMaxBytes();

  /**
   * Gets the number of minutes to retain clones.
   *
   * @return The number of minutes
   */
  protected abstract int getCloneRetentionMinutes();

  /**
   * Hashes the coordinates of an artifact.
   *
   * @param artifactName The name or URL of the artifact
   * @param artifactVersion The version, branch, or tag of the artifact
   * @return The hash of the coordinates
   */
  protected String hashCoordinates(String artifactName, String artifactVersion) {
    String coordinates =
        String.format(
            "%s-%s",
            Optional.ofNullable(artifactName).orElse("unknownUrl"),
            Optional.ofNullable(artifactVersion).orElse("defaultVersion"));
    return Hashing.sha256().hashString(coordinates, Charset.defaultCharset()).toString();
  }

  /**
   * Deletes expired clones.
   *
   * @param logPrefix A prefix to use in log messages
   */
  protected void deleteExpiredClones(String logPrefix) {
    try {
      if (!clonesHome.toFile().exists() || getCloneRetentionMinutes() < 0) {
        return;
      }
      File[] clones = clonesHome.toFile().listFiles();
      if (clones == null) {
        return;
      }
      for (File clone : clones) {
        long ageMin = ((System.currentTimeMillis() - clone.lastModified()) / 1000) / 60;
        if (ageMin < getCloneRetentionMinutes()) {
          continue;
        }
        if (!tryLock(clone.getName())) {
          // move on if the directory is locked by another thread, just wait for the next cycle
          continue;
        }
        try {
          log.info("Deleting expired {} clone {}", logPrefix, clone.getName());
          FileUtils.forceDelete(clone);
        } finally {
          unlock(clone.getName());
        }
      }
    } catch (IOException e) {
      log.error("Error deleting expired {} clones, ignoring", logPrefix, e);
    }
  }
}
