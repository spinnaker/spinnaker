/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Atomic operation that uploads an artifact to S3.
 *
 * <p>This operation:
 *
 * <ol>
 *   <li>Downloads the specified artifact using {@link ArtifactDownloader}
 *   <li>Extracts the archive (ZIP, TAR, or TAR.GZ) to a temporary directory using {@link
 *       ArchiveExtractor}
 *   <li>Uploads all extracted files to the specified S3 bucket
 *   <li>Preserves directory structure from the archive
 *   <li>Cleans up temporary files after completion
 * </ol>
 *
 * <p>The operation returns a map containing:
 *
 * <ul>
 *   <li>{@code destinationUri} - the S3 URI where files were uploaded (e.g., s3://bucket/prefix/)
 *   <li>{@code fileCount} - the number of files uploaded
 * </ul>
 */
@Slf4j
public class UploadArtifactToS3AtomicOperation implements AtomicOperation<Map<String, Object>> {

  private static final String BASE_PHASE = "UPLOAD_ARTIFACT_TO_S3";

  private final UploadArtifactToS3Description description;
  private final AmazonClientProvider amazonClientProvider;
  private final ArtifactDownloader artifactDownloader;

  public UploadArtifactToS3AtomicOperation(
      UploadArtifactToS3Description description,
      AmazonClientProvider amazonClientProvider,
      ArtifactDownloader artifactDownloader) {
    this.description = description;
    this.amazonClientProvider = amazonClientProvider;
    this.artifactDownloader = artifactDownloader;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Map<String, Object> operate(List<Map<String, Object>> priorOutputs) {
    String bucket = description.getBucket();
    String prefix = description.getObjectKeyPrefix();
    if (prefix == null) {
      prefix = "";
    }
    Artifact artifact = description.getArtifact();

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Downloading artifact: "
                + (artifact.getName() != null ? artifact.getName() : artifact.getReference()));

    Path tempDir = null;
    try {
      // Create temp directory for extraction
      tempDir = Files.createTempDirectory("s3-upload-");

      // Determine filename for format detection
      String filename = determineFilename(artifact);
      Path archiveFile = tempDir.resolve(filename);

      // Download artifact
      try (InputStream inputStream = artifactDownloader.download(artifact)) {
        Files.copy(inputStream, archiveFile);
      }

      // Extract archive
      getTask().updateStatus(BASE_PHASE, "Extracting archive: " + filename);
      Path extractDir = tempDir.resolve("extracted");
      List<Path> extractedFiles = ArchiveExtractor.extract(archiveFile, extractDir);

      getTask()
          .updateStatus(
              BASE_PHASE,
              "Syncing " + extractedFiles.size() + " files to s3://" + bucket + "/" + prefix);

      S3Client s3Client =
          amazonClientProvider.getAmazonS3V2(description.getCredentials(), description.getRegion());
      try (S3ArchiveUpload uploader = new S3ArchiveUpload(s3Client)) {
        uploader
            .uploadFileList(
                bucket,
                prefix.replaceAll("/+$", ""),
                extractDir.toFile(),
                extractedFiles.stream().map(extractDir::resolve).map(Path::toFile).toList(),
                (file, metadata) -> {},
                null)
            .waitForCompletion();
      }

      String destinationUri =
          prefix.isEmpty() ? "s3://" + bucket + "/" : "s3://" + bucket + "/" + prefix + "/";
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Successfully uploaded " + extractedFiles.size() + " files to " + destinationUri);

      Map<String, Object> result = new HashMap<>();
      result.put("destinationUri", destinationUri);
      result.put("fileCount", extractedFiles.size());
      return result;
    } catch (IOException e) {
      log.error("Failed to upload artifact to S3", e);
      getTask().updateStatus(BASE_PHASE, "Failed to upload artifact: " + e.getMessage());
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Upload interrupted", e);
    } finally {
      if (tempDir != null) {
        try {
          deleteDirectory(tempDir);
        } catch (IOException e) {
          log.error("Failed to cleanup temp directory: {}", tempDir, e);
        }
      }
    }
  }

  /**
   * Determines the filename for the artifact, used for archive format detection.
   *
   * <p>Tries in order: artifact name, last segment of reference URL, or defaults to "artifact.zip".
   */
  private String determineFilename(Artifact artifact) {
    if (artifact.getName() != null && !artifact.getName().isEmpty()) {
      return artifact.getName();
    }
    if (artifact.getReference() != null) {
      String[] parts = artifact.getReference().split("/");
      if (parts.length > 0) {
        return parts[parts.length - 1];
      }
    }
    return "artifact.zip";
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * <p>Files are deleted before their parent directories by sorting paths in reverse order.
   */
  private void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (var paths = Files.walk(directory)) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    log.warn("Failed to delete: {}", path, e);
                  }
                });
      }
    }
  }
}
