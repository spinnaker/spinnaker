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

import static com.amazonaws.util.ValidationUtils.assertNotNull;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbstractArtifactCachedFileSystemTest {

  @TempDir Path tempDir;
  private TestArtifactCachedFileSystem fileSystem;
  private static final String TEST_CLONES_DIR = "test-clones";
  private static final String TEST_ARTIFACT_NAME = "test-artifact";
  private static final String TEST_ARTIFACT_VERSION = "1.0.0";

  // Concrete implementation of the abstract class for testing
  private static class TestArtifactCachedFileSystem extends AbstractArtifactCachedFileSystem {
    private final int waitLockTimeoutSec;
    private final long retentionMaxBytes;
    private final int retentionMinutes;
    private final boolean canRetain;

    public TestArtifactCachedFileSystem(
        String clonesDirectoryName,
        int waitLockTimeoutSec,
        long retentionMaxBytes,
        int retentionMinutes,
        boolean canRetain) {
      super(clonesDirectoryName);
      this.waitLockTimeoutSec = waitLockTimeoutSec;
      this.retentionMaxBytes = retentionMaxBytes;
      this.retentionMinutes = retentionMinutes;
      this.canRetain = canRetain;
    }

    @Override
    public int getCloneWaitLockTimeoutSec() {
      return waitLockTimeoutSec;
    }

    @Override
    protected long getCloneRetentionMaxBytes() {
      return retentionMaxBytes;
    }

    @Override
    protected int getCloneRetentionMinutes() {
      return retentionMinutes;
    }

    @Override
    public boolean canRetainClone() {
      return canRetain;
    }

    // Expose protected methods for testing
    public void deleteExpiredClonesForTest(String logPrefix) {
      deleteExpiredClones(logPrefix);
    }

    public String getHashForTest(String artifactName, String artifactVersion) {
      return hashCoordinates(artifactName, artifactVersion);
    }

    public boolean hasFreeDiskForTest() {
      return hasFreeDisk();
    }

    public Path getClonesHome() {
      return clonesHome;
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    // Create file system with test parameters
    fileSystem =
        new TestArtifactCachedFileSystem(
            TEST_CLONES_DIR,
            5, // 5 seconds timeout
            1024 * 1024 * 10, // 10MB max
            60, // 60 minutes retention
            true // Can retain
            );

    // Create the clones directory if it doesn't exist
    Files.createDirectories(fileSystem.getClonesHome());
  }

  @AfterEach
  void tearDown() throws IOException {
    // Clean up any files created during tests
    if (Files.exists(fileSystem.getClonesHome())) {
      FileUtils.deleteDirectory(fileSystem.getClonesHome().toFile());
    }
  }

  @Test
  void testGetLocalClonePath() {
    // Get path for test artifact
    Path clonePath = fileSystem.getLocalClonePath(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);

    // Verify path is correct
    String expectedHash = fileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    Path expectedPath = fileSystem.getClonesHome().resolve(expectedHash);
    assertEquals(expectedPath, clonePath, "Local clone path should match expected path");
  }

  @Test
  void testGetLocalClonePathWithNullValues() {
    // Test with null artifact name
    Path clonePath1 = fileSystem.getLocalClonePath(null, TEST_ARTIFACT_VERSION);
    assertNotNull(clonePath1, "Path should not be null for null artifact name");

    // Test with null version
    Path clonePath2 = fileSystem.getLocalClonePath(TEST_ARTIFACT_NAME, null);
    assertNotNull(clonePath2, "Path should not be null for null artifact version");

    // Test with both null
    Path clonePath3 = fileSystem.getLocalClonePath(null, null);
    assertNotNull(clonePath3, "Path should not be null for null artifact name and version");

    // Verify paths are different
    assertNotEquals(clonePath1, clonePath2, "Paths should be different for different inputs");
    assertNotEquals(clonePath1, clonePath3, "Paths should be different for different inputs");
    assertNotEquals(clonePath2, clonePath3, "Paths should be different for different inputs");
  }

  @Test
  void testTryTimedLock() throws InterruptedException {
    // Acquire lock
    boolean locked = fileSystem.tryTimedLock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    assertTrue(locked, "Should be able to acquire lock");

    // Try to acquire same lock again (should fail)
    Thread thread =
        new Thread(
            () -> {
              try {
                boolean lockedAgain =
                    fileSystem.tryTimedLock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
                assertFalse(lockedAgain, "Should not be able to acquire lock twice");
              } catch (InterruptedException e) {
                fail("Thread was interrupted: " + e.getMessage());
              }
            });
    thread.start();
    thread.join();

    // Release lock
    fileSystem.unlock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);

    // Try to acquire lock again (should succeed)
    boolean lockedAfterRelease = fileSystem.tryTimedLock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    assertTrue(lockedAfterRelease, "Should be able to acquire lock after release");

    // Clean up
    fileSystem.unlock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
  }

  @Test
  void testTryLock() throws InterruptedException {
    // Get hash for test artifact
    String hash = fileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);

    // Acquire lock
    boolean locked = fileSystem.tryLock(hash);
    assertTrue(locked, "Should be able to acquire lock");

    // Try to acquire same lock again (should fail)
    Thread thread =
        new Thread(
            () -> {
              boolean lockedAgain = fileSystem.tryLock(hash);
              assertFalse(lockedAgain, "Should not be able to acquire lock twice");
            });
    thread.start();
    thread.join();

    // Release lock
    fileSystem.unlock(hash);

    // Try to acquire lock again (should succeed)
    boolean lockedAfterRelease = fileSystem.tryLock(hash);
    assertTrue(lockedAfterRelease, "Should be able to acquire lock after release");

    // Clean up
    fileSystem.unlock(hash);
  }

  @Test
  void testUnlockWithArtifactCoordinates() {
    // Acquire lock
    boolean locked =
        fileSystem.tryLock(fileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION));
    assertTrue(locked, "Should be able to acquire lock");

    // Release lock using artifact coordinates
    fileSystem.unlock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);

    // Try to acquire lock again (should succeed if unlock worked)
    boolean lockedAfterRelease =
        fileSystem.tryLock(fileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION));
    assertTrue(lockedAfterRelease, "Should be able to acquire lock after release");

    // Clean up
    fileSystem.unlock(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
  }

  @Test
  void testUnlockNonExistentLock() {
    // This should not throw an exception
    fileSystem.unlock("non-existent-hash");
  }

  @Test
  void testHashCoordinates() {
    // Test normal case
    String hash = fileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    assertNotNull(hash, "Hash should not be null");
    assertFalse(hash.isEmpty(), "Hash should not be empty");

    // Test with null artifact name
    String hash1 = fileSystem.getHashForTest(null, TEST_ARTIFACT_VERSION);
    assertNotNull(hash1, "Hash should not be null for null artifact name");

    // Test with null version
    String hash2 = fileSystem.getHashForTest(TEST_ARTIFACT_NAME, null);
    assertNotNull(hash2, "Hash should not be null for null artifact version");

    // Test with both null
    String hash3 = fileSystem.getHashForTest(null, null);
    assertNotNull(hash3, "Hash should not be null for null artifact name and version");

    // Verify hashes are different
    assertNotEquals(hash, hash1, "Hashes should be different for different inputs");
    assertNotEquals(hash, hash2, "Hashes should be different for different inputs");
    assertNotEquals(hash, hash3, "Hashes should be different for different inputs");
    assertNotEquals(hash1, hash2, "Hashes should be different for different inputs");
    assertNotEquals(hash1, hash3, "Hashes should be different for different inputs");
    assertNotEquals(hash2, hash3, "Hashes should be different for different inputs");
  }

  @Test
  void testHasFreeDisk() throws IOException {
    // Should return true initially (no files)
    assertTrue(fileSystem.hasFreeDiskForTest(), "Should have free disk space initially");

    // Create a small file to simulate some disk usage
    Files.createDirectories(fileSystem.getClonesHome());
    Path testFile = fileSystem.getClonesHome().resolve("test-file");
    Files.write(testFile, new byte[1024]); // 1KB file

    // Should still return true since we're well under the 10MB limit
    assertTrue(fileSystem.hasFreeDiskForTest(), "Should have free disk space with small file");
  }

  @Test
  void testHasFreeDiskWhenOverLimit() throws IOException {
    // Create a file system with a very small limit
    TestArtifactCachedFileSystem smallLimitFileSystem =
        new TestArtifactCachedFileSystem(
            TEST_CLONES_DIR,
            5,
            100, // Only 100 bytes allowed
            60,
            true);

    // Create the directory
    Files.createDirectories(smallLimitFileSystem.getClonesHome());

    // Create a file larger than the limit
    Path testFile = smallLimitFileSystem.getClonesHome().resolve("test-file");
    Files.write(testFile, new byte[1024]); // 1KB file

    // Should return false since we're over the 100 byte limit
    assertFalse(
        smallLimitFileSystem.hasFreeDiskForTest(),
        "Should not have free disk space when over limit");
  }

  @Test
  void testCanRetainClone() {
    // Test with a file system that can retain clones
    TestArtifactCachedFileSystem canRetainFileSystem =
        new TestArtifactCachedFileSystem(TEST_CLONES_DIR, 5, 1024 * 1024, 60, true);
    assertTrue(canRetainFileSystem.canRetainClone(), "Should be able to retain clone");

    // Test with a file system that cannot retain clones
    TestArtifactCachedFileSystem cannotRetainFileSystem =
        new TestArtifactCachedFileSystem(TEST_CLONES_DIR, 5, 1024 * 1024, 60, false);
    assertFalse(cannotRetainFileSystem.canRetainClone(), "Should not be able to retain clone");
  }

  @Test
  void testDeleteExpiredClones() throws IOException, InterruptedException {
    // Create a file system with a very short retention period
    TestArtifactCachedFileSystem shortRetentionFileSystem =
        new TestArtifactCachedFileSystem(
            TEST_CLONES_DIR,
            5,
            1024 * 1024,
            0, // 0 minutes retention (everything is expired)
            true);

    // Create the directory
    Files.createDirectories(shortRetentionFileSystem.getClonesHome());

    // Create a test directory that simulates a clone
    String hash =
        shortRetentionFileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    Path cloneDir = shortRetentionFileSystem.getClonesHome().resolve(hash);
    Files.createDirectories(cloneDir);

    // Create a test file in the clone directory
    Path testFile = cloneDir.resolve("test-file");
    Files.write(testFile, new byte[10]);

    // Make sure the file exists
    assertTrue(Files.exists(testFile), "Test file should exist before cleanup");

    // Set the last modified time to the past to ensure it's expired
    File cloneDirFile = cloneDir.toFile();
    cloneDirFile.setLastModified(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10));

    // Run cleanup
    shortRetentionFileSystem.deleteExpiredClonesForTest("test");

    // Verify the directory was deleted
    assertFalse(Files.exists(cloneDir), "Clone directory should be deleted after cleanup");
  }

  @Test
  void testDeleteExpiredClonesWithRecentFiles() throws IOException {
    // Create a test directory that simulates a clone
    Files.createDirectories(fileSystem.getClonesHome());
    String hash = fileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    Path cloneDir = fileSystem.getClonesHome().resolve(hash);
    Files.createDirectories(cloneDir);

    // Create a test file in the clone directory
    Path testFile = cloneDir.resolve("test-file");
    Files.write(testFile, new byte[10]);

    // Run cleanup (should not delete anything since the file is recent)
    fileSystem.deleteExpiredClonesForTest("test");

    // Verify the directory still exists
    assertTrue(Files.exists(cloneDir), "Clone directory should still exist after cleanup");
  }

  @Test
  void testDeleteExpiredClonesWithLockedDirectory() throws IOException, InterruptedException {
    // Create a file system with a very short retention period
    TestArtifactCachedFileSystem shortRetentionFileSystem =
        new TestArtifactCachedFileSystem(
            TEST_CLONES_DIR,
            5,
            1024 * 1024,
            0, // 0 minutes retention (everything is expired)
            true);

    // Create the directory
    Files.createDirectories(shortRetentionFileSystem.getClonesHome());

    // Create a test directory that simulates a clone
    String hash =
        shortRetentionFileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    Path cloneDir = shortRetentionFileSystem.getClonesHome().resolve(hash);
    Files.createDirectories(cloneDir);

    // Create a test file in the clone directory
    Path testFile = cloneDir.resolve("test-file");
    Files.write(testFile, new byte[10]);

    // Set the last modified time to the past to ensure it's expired
    File cloneDirFile = cloneDir.toFile();
    cloneDirFile.setLastModified(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10));

    // Acquire a lock on the directory
    boolean locked = shortRetentionFileSystem.tryLock(hash);
    assertTrue(locked, "Should be able to acquire lock");

    try {
      Thread thread =
          new Thread(
              () -> {
                // Run cleanup (should skip the locked directory)
                shortRetentionFileSystem.deleteExpiredClonesForTest("test");
                // Verify the directory still exists
                assertTrue(Files.exists(cloneDir), "Locked clone directory should not be deleted");
              });
      thread.start();
      thread.join();
    } finally {
      // Release the lock
      shortRetentionFileSystem.unlock(hash);
    }
  }

  @Test
  void testDeleteExpiredClonesWithNegativeRetention() throws IOException {
    // Create a file system with a negative retention period (disabled cleanup)
    TestArtifactCachedFileSystem disabledCleanupFileSystem =
        new TestArtifactCachedFileSystem(
            TEST_CLONES_DIR,
            5,
            1024 * 1024,
            -1, // Negative retention (cleanup disabled)
            true);

    // Create the directory
    Files.createDirectories(disabledCleanupFileSystem.getClonesHome());

    // Create a test directory that simulates a clone
    String hash =
        disabledCleanupFileSystem.getHashForTest(TEST_ARTIFACT_NAME, TEST_ARTIFACT_VERSION);
    Path cloneDir = disabledCleanupFileSystem.getClonesHome().resolve(hash);
    Files.createDirectories(cloneDir);

    // Create a test file in the clone directory
    Path testFile = cloneDir.resolve("test-file");
    Files.write(testFile, new byte[10]);

    // Set the last modified time to the past
    File cloneDirFile = cloneDir.toFile();
    cloneDirFile.setLastModified(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10));

    // Run cleanup (should not delete anything due to negative retention)
    disabledCleanupFileSystem.deleteExpiredClonesForTest("test");

    // Verify the directory still exists
    assertTrue(
        Files.exists(cloneDir), "Clone directory should still exist with negative retention");
  }

  @Test
  void testDeleteExpiredClonesWithNonExistentDirectory() {
    // Create a file system with a non-existent directory
    TestArtifactCachedFileSystem nonExistentDirFileSystem =
        new TestArtifactCachedFileSystem(
            "non-existent-dir-" + System.currentTimeMillis(), 5, 1024 * 1024, 0, true);

    // Run cleanup (should not throw an exception)
    assertDoesNotThrow(
        () -> nonExistentDirFileSystem.deleteExpiredClonesForTest("test"),
        "Should not throw exception for non-existent directory");
  }

  @Test
  void testConcurrentLocking() throws InterruptedException {
    // Test that multiple threads can acquire different locks
    String hash1 = fileSystem.getHashForTest("artifact1", "version1");
    String hash2 = fileSystem.getHashForTest("artifact2", "version2");

    // Acquire first lock in main thread
    boolean locked1 = fileSystem.tryLock(hash1);
    assertTrue(locked1, "Should be able to acquire first lock");

    // Acquire second lock in separate thread
    Thread thread =
        new Thread(
            () -> {
              boolean locked2 = fileSystem.tryLock(hash2);
              assertTrue(locked2, "Should be able to acquire second lock");
              fileSystem.unlock(hash2);
            });
    thread.start();
    thread.join();

    // Release first lock
    fileSystem.unlock(hash1);
  }
}
