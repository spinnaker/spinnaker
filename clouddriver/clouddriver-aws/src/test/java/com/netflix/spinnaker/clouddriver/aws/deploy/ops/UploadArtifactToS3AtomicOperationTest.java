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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.aws.TestCredential;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

class UploadArtifactToS3AtomicOperationTest {

  private S3Client amazonS3;
  private AmazonClientProvider amazonClientProvider;
  private ArtifactDownloader artifactDownloader;
  private Task task;

  @BeforeEach
  void setUp() {
    amazonS3 = mock(S3Client.class);
    amazonClientProvider = mock(AmazonClientProvider.class);
    artifactDownloader = mock(ArtifactDownloader.class);
    task = mock(Task.class);

    TaskRepository.threadLocalTask.set(task);
    when(amazonClientProvider.getAmazonS3V2(any(), anyString())).thenReturn(amazonS3);
  }

  @Test
  void shouldDownloadArtifactExtractAndSyncToS3() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "dir/file2.txt"}, new String[] {"content1", "content2"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3, times(2)).putObject(requests.capture(), any(RequestBody.class));
    assertTrue(requests.getAllValues().stream().allMatch(r -> r.bucket().equals("my-bucket")));
    assertEquals(
        java.util.Set.of("releases/v1/file1.txt", "releases/v1/dir/file2.txt"),
        requests.getAllValues().stream()
            .map(PutObjectRequest::key)
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals("s3://my-bucket/releases/v1/", result.get("destinationUri"));
    assertEquals(2, result.get("fileCount"));
  }

  @Test
  void shouldHandleEmptyObjectKeyPrefix() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("file.txt", request.getValue().key());
    assertEquals("s3://my-bucket/", result.get("destinationUri"));
  }

  @Test
  void shouldNormalizeTrailingSlashInPrefix() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("releases/v1/");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("releases/v1/file.txt", request.getValue().key());
    assertEquals("s3://my-bucket/releases/v1/", result.get("destinationUri"));
  }

  @Test
  void shouldNormalizeLeadingSlashInPrefix() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("config.json")
            .reference("http://example.com/config.json")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("/configs/latest/");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(
            new ByteArrayInputStream("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8)));
    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("configs/latest/config.json", request.getValue().key());
    assertEquals("s3://my-bucket/configs/latest/config.json", result.get("destinationUri"));
  }

  @Test
  void shouldUpdateTaskStatusDuringOperation() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    verify(task, atLeastOnce()).updateStatus(eq("UPLOAD_ARTIFACT_TO_S3"), anyString());
  }

  @Test
  void shouldNotListOrDeleteWhenDeleteStaleFilesIsFalse() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(false);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    verify(amazonS3, never()).listObjectsV2(any(ListObjectsV2Request.class));
    verify(amazonS3, never()).deleteObjects(any(DeleteObjectsRequest.class));
    assertEquals(false, result.get("deleteStaleFiles"));
    assertEquals(0, result.get("deletedCount"));
  }

  @Test
  void shouldDeleteStaleObjectsWhenDeleteStaleFilesIsTrue() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "dir/file2.txt"}, new String[] {"content1", "content2"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(true);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Mock S3 listing with stale objects
    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder()
            .contents(
                S3Object.builder().key("prefix/file1.txt").build(),
                S3Object.builder().key("prefix/dir/file2.txt").build(),
                S3Object.builder().key("prefix/old-file.txt").build())
            .isTruncated(false)
            .build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);
    when(amazonS3.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(DeleteObjectsResponse.builder().build());

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(true, result.get("deleteStaleFiles"));
    assertEquals(1, result.get("deletedCount"));

    ArgumentCaptor<DeleteObjectsRequest> deleteCaptor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(amazonS3).deleteObjects(deleteCaptor.capture());

    List<String> deletedKeys =
        deleteCaptor.getValue().delete().objects().stream().map(ObjectIdentifier::key).toList();
    assertEquals(1, deletedKeys.size());
    assertTrue(deletedKeys.contains("prefix/old-file.txt"));
  }

  @Test
  void shouldNotCallDeleteObjectsWhenNoStaleObjects() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(true);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Mock S3 listing with only the uploaded file
    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder()
            .contents(S3Object.builder().key("prefix/file.txt").build())
            .isTruncated(false)
            .build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(true, result.get("deleteStaleFiles"));
    assertEquals(0, result.get("deletedCount"));
    verify(amazonS3, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void shouldDeleteNestedStaleObjectsRecursively() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"index.html"}, new String[] {"<html/>"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("http://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(true);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Mock S3 listing with nested stale objects in a directory tree
    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder()
            .contents(
                S3Object.builder().key("prefix/index.html").build(),
                S3Object.builder().key("prefix/old-dir/sub/file.txt").build(),
                S3Object.builder().key("prefix/old-dir/sub/other.txt").build(),
                S3Object.builder().key("prefix/old-dir/root.txt").build())
            .isTruncated(false)
            .build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);
    when(amazonS3.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(DeleteObjectsResponse.builder().build());

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(true, result.get("deleteStaleFiles"));
    assertEquals(3, result.get("deletedCount"));

    ArgumentCaptor<DeleteObjectsRequest> deleteCaptor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(amazonS3).deleteObjects(deleteCaptor.capture());

    List<String> deletedKeys =
        deleteCaptor.getValue().delete().objects().stream()
            .map(ObjectIdentifier::key)
            .sorted()
            .toList();
    assertEquals(3, deletedKeys.size());
    assertTrue(deletedKeys.contains("prefix/old-dir/sub/file.txt"));
    assertTrue(deletedKeys.contains("prefix/old-dir/sub/other.txt"));
    assertTrue(deletedKeys.contains("prefix/old-dir/root.txt"));
  }

  @Test
  void shouldUploadSingleFileWhenExtractArchiveIsFalse() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("config.json")
            .reference("http://example.com/config.json")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("configs/latest");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(
            new ByteArrayInputStream("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8)));
    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("my-bucket", request.getValue().bucket());
    assertEquals("configs/latest/config.json", request.getValue().key());

    assertEquals(1, result.get("fileCount"));
    assertEquals("s3://my-bucket/configs/latest/config.json", result.get("destinationUri"));
    assertEquals(false, result.get("deleteStaleFiles"));
    assertEquals(0, result.get("deletedCount"));
  }

  @Test
  void shouldNotDeleteStaleFilesWhenExtractArchiveIsFalse() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("config.json")
            .reference("http://example.com/config.json")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("configs");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setDeleteStaleFiles(true); // explicitly set, should still be forced off
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    // Even though deleteStaleFiles=true in description, single file mode forces it off
    verify(amazonS3, never()).listObjectsV2(any(ListObjectsV2Request.class));
    verify(amazonS3, never()).deleteObjects(any(DeleteObjectsRequest.class));
    assertEquals(false, result.get("deleteStaleFiles"));
    assertEquals(0, result.get("deletedCount"));
  }

  @Test
  void shouldUploadSingleFileWithEmptyPrefix() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("data.yaml")
            .reference("http://example.com/data.yaml")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("key: value".getBytes(StandardCharsets.UTF_8)));
    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(request.capture(), any(RequestBody.class));
    assertEquals("data.yaml", request.getValue().key());

    assertEquals("s3://my-bucket/data.yaml", result.get("destinationUri"));
    assertEquals(1, result.get("fileCount"));
  }

  private byte[] createTestZip(String[] paths, String[] contents) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (int i = 0; i < paths.length; i++) {
        zos.putNextEntry(new ZipEntry(paths[i]));
        zos.write(contents[i].getBytes());
        zos.closeEntry();
      }
    }
    return baos.toByteArray();
  }
}
