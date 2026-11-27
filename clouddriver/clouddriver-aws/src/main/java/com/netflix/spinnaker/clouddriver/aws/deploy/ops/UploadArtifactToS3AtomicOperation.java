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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

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
 *   <li>{@code deleteStaleFiles} - whether stale files were deleted
 *   <li>{@code deletedCount} - the number of stale files deleted
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
    String prefix = normalizePrefix(description.getObjectKeyPrefix());
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

      S3Client s3Client =
          amazonClientProvider.getAmazonS3V2(description.getCredentials(), description.getRegion());
      if (description.isExtractArchive()) {
        return uploadExtractedArchive(s3Client, bucket, prefix, filename, archiveFile, tempDir);
      } else {
        return uploadSingleFile(s3Client, bucket, prefix, filename, archiveFile);
      }
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
   * Extracts the archive and uploads all extracted files to S3. If {@code deleteStaleFiles} is
   * enabled, objects under the prefix that are not present in the archive are removed after upload.
   */
  private Map<String, Object> uploadExtractedArchive(
      S3Client s3Client,
      String bucket,
      String prefix,
      String filename,
      Path archiveFile,
      Path tempDir)
      throws IOException, InterruptedException {
    getTask().updateStatus(BASE_PHASE, "Extracting archive: " + filename);
    Path extractDir = tempDir.resolve("extracted");
    List<Path> extractedFiles = ArchiveExtractor.extract(archiveFile, extractDir);

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Syncing " + extractedFiles.size() + " files to s3://" + bucket + "/" + prefix);

    try (S3ArchiveUpload uploader = new S3ArchiveUpload(s3Client)) {
      uploader
          .uploadFileList(
              bucket,
              prefix,
              extractDir.toFile(),
              extractedFiles.stream().map(extractDir::resolve).map(Path::toFile).toList(),
              (file, metadata) -> {
                metadata.contentType(detectContentType(file.getName()));
                String cacheControl = description.getCacheControl();
                if (cacheControl != null && !cacheControl.isBlank()) {
                  metadata.cacheControl(cacheControl);
                }
              },
              null)
          .waitForCompletion();
    }

    // if enabled, remove objects from destination that are not in the uploaded artifact
    int deletedCount = 0;
    if (description.isDeleteStaleFiles()) {
      deletedCount = deleteStaleFiles(s3Client, bucket, prefix, extractedFiles);
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
    result.put("deleteStaleFiles", description.isDeleteStaleFiles());
    result.put("deletedCount", deletedCount);
    return result;
  }

  /**
   * Uploads the downloaded artifact as a single file to S3. Stale file deletion is always disabled
   * for single-file uploads.
   */
  private Map<String, Object> uploadSingleFile(
      S3Client s3Client, String bucket, String prefix, String filename, Path file) {
    String key = prefix.isEmpty() ? filename : prefix + "/" + filename;
    String destinationUri = "s3://" + bucket + "/" + key;

    getTask().updateStatus(BASE_PHASE, "Uploading single file to " + destinationUri);

    String contentType = description.getContentType();
    PutObjectRequest.Builder request =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(
                contentType != null && !contentType.isBlank()
                    ? contentType
                    : detectContentType(filename));
    String cacheControl = description.getCacheControl();
    if (cacheControl != null && !cacheControl.isBlank()) {
      request.cacheControl(cacheControl);
    }
    s3Client.putObject(request.build(), RequestBody.fromFile(file));

    getTask().updateStatus(BASE_PHASE, "Successfully uploaded file to " + destinationUri);

    Map<String, Object> result = new HashMap<>();
    result.put("destinationUri", destinationUri);
    result.put("fileCount", 1);
    result.put("deleteStaleFiles", false);
    result.put("deletedCount", 0);
    return result;
  }

  /**
   * Deletes S3 objects under the given prefix that are not present in the uploaded file set.
   *
   * @return the number of objects deleted
   */
  private int deleteStaleFiles(
      S3Client s3Client, String bucket, String prefix, List<Path> extractedFiles) {
    Set<String> expectedKeys = new HashSet<>();
    for (Path file : extractedFiles) {
      String relativePath = file.toString().replace('\\', '/');
      String key = prefix.isEmpty() ? relativePath : prefix + "/" + relativePath;
      expectedKeys.add(key);
    }

    // List all objects under the prefix
    List<String> staleKeys = new ArrayList<>();
    String listPrefix = prefix.isEmpty() ? "" : prefix + "/";
    String continuationToken = null;
    ListObjectsV2Response listResult;
    do {
      listResult =
          s3Client.listObjectsV2(
              ListObjectsV2Request.builder()
                  .bucket(bucket)
                  .prefix(listPrefix)
                  .continuationToken(continuationToken)
                  .build());
      for (S3Object summary : listResult.contents()) {
        if (!expectedKeys.contains(summary.key())) {
          staleKeys.add(summary.key());
        }
      }
      continuationToken = listResult.nextContinuationToken();
    } while (Boolean.TRUE.equals(listResult.isTruncated()));

    if (staleKeys.isEmpty()) {
      getTask().updateStatus(BASE_PHASE, "No stale files to remove");
      return 0;
    }

    // Batch delete in groups of 1000 (S3 API limit)
    int deleted = 0;
    for (int i = 0; i < staleKeys.size(); i += 1000) {
      List<String> batch = staleKeys.subList(i, Math.min(i + 1000, staleKeys.size()));
      DeleteObjectsRequest deleteRequest =
          DeleteObjectsRequest.builder()
              .bucket(bucket)
              .delete(
                  Delete.builder()
                      .objects(
                          batch.stream()
                              .map(key -> ObjectIdentifier.builder().key(key).build())
                              .toList())
                      .quiet(true)
                      .build())
              .build();
      DeleteObjectsResponse response = s3Client.deleteObjects(deleteRequest);
      if (!response.errors().isEmpty()) {
        throw S3Exception.builder()
            .statusCode(200)
            .message("S3 returned errors while deleting stale artifact files")
            .build();
      }
      deleted += batch.size();
    }

    getTask().updateStatus(BASE_PHASE, "Deleted " + deleted + " stale files from destination");
    return deleted;
  }

  /**
   * Detects the content type for a file based on its name/extension using Spring Boot's MIME
   * mappings with custom overrides. Falls back to {@code application/octet-stream} when the type
   * cannot be determined.
   */
  static String detectContentType(String filename) {
    return ContentTypeDetector.detectContentType(filename);
  }

  /**
   * Normalizes the object key prefix by stripping leading and trailing slashes. Returns an empty
   * string for null or blank input.
   */
  private static String normalizePrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return "";
    }
    return prefix.replaceAll("^/+|/+$", "");
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
