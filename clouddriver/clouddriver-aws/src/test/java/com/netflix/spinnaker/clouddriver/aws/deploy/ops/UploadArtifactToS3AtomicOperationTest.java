/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    lenient()
        .when(amazonS3.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenReturn(DeleteObjectsResponse.builder().build());
    lenient()
        .when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());
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
            .reference("https://example.com/test.zip")
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
    assertEquals(2, result.get("uploadCount"));
    assertEquals(0, result.get("skippedCount"));
  }

  @Test
  void shouldHandleEmptyObjectKeyPrefix() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
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
            .reference("https://example.com/test.zip")
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
            .reference("https://example.com/config.json")
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
            .reference("https://example.com/test.zip")
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
            .reference("https://example.com/test.zip")
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

    // listObjectsV2 is called once for ETag sync, but not for stale file deletion
    verify(amazonS3, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
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
            .reference("https://example.com/test.zip")
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
            .reference("https://example.com/test.zip")
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
    verify(amazonS3, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
    verify(amazonS3, never()).deleteObjects(any(DeleteObjectsRequest.class));
  }

  @Test
  void shouldDeleteNestedStaleObjectsRecursively() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"index.html"}, new String[] {"<html/>"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
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
            .reference("https://example.com/config.json")
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
            .reference("https://example.com/config.json")
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
            .reference("https://example.com/data.yaml")
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

  @Test
  void shouldSetContentTypeOnSingleFileUpload() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("index.html")
            .reference("https://example.com/index.html")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("<html/>".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertEquals("text/html", requestCaptor.getValue().contentType());
  }

  @Test
  void shouldSetCacheControlOnSingleFileUpload() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("app.js")
            .reference("https://example.com/app.js")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("assets");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCacheControl("max-age=31536000, public");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("var x=1;".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertEquals("max-age=31536000, public", requestCaptor.getValue().cacheControl());
  }

  @Test
  void shouldNotSetCacheControlWhenNull() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("app.js")
            .reference("https://example.com/app.js")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("assets");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("var x=1;".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertNull(requestCaptor.getValue().cacheControl());
  }

  @Test
  void shouldNotSetCacheControlWhenBlank() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("app.js")
            .reference("https://example.com/app.js")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("assets");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCacheControl("   ");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("var x=1;".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertNull(requestCaptor.getValue().cacheControl());
  }

  @Test
  void shouldPassMetadataProviderToUploadFileList() throws Exception {
    byte[] zipContent =
        createTestZip(new String[] {"index.html", "app.js"}, new String[] {"<html/>", "var x=1;"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setCacheControl("max-age=3600");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3, times(2)).putObject(requests.capture(), any(RequestBody.class));
    assertTrue(
        requests.getAllValues().stream().allMatch(r -> "max-age=3600".equals(r.cacheControl())));
    assertTrue(requests.getAllValues().stream().anyMatch(r -> "text/html".equals(r.contentType())));
  }

  @Test
  void shouldSetExpiresOnArchiveUploadMetadata() throws Exception {
    byte[] zipContent =
        createTestZip(new String[] {"index.html", "app.js"}, new String[] {"<html/>", "var x=1;"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setExpires("P1Y");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);
    operation.setClock(Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC));

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3, times(2)).putObject(requests.capture(), any(RequestBody.class));
    assertTrue(
        requests.getAllValues().stream()
            .allMatch(r -> Instant.parse("2027-03-31T12:00:00Z").equals(r.expires())));
  }

  @Test
  void shouldUseContentTypeOverrideOnSingleFileUpload() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("current")
            .reference("https://example.com/current")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("versions");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setContentType("text/plain");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("v1.2.3".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertEquals("text/plain", requestCaptor.getValue().contentType());
  }

  @Test
  void shouldSetExpiresOnSingleFileUpload() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("current")
            .reference("https://example.com/current")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("versions");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setExpires("PT6H");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("v1.2.3".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);
    operation.setClock(Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC));

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertEquals(Instant.parse("2026-03-31T18:00:00Z"), requestCaptor.getValue().expires());
  }

  @Test
  void shouldThrowWhenExpiresIsInvalid() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("current")
            .reference("https://example.com/current")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("versions");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setExpires("1 year");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("v1.2.3".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);
    operation.setClock(Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> operation.operate(Collections.emptyList()));

    assertEquals("Invalid expires value: 1 year", exception.getMessage());
  }

  @Test
  void shouldThrowWhenExpiresIsNonPositive() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("current")
            .reference("https://example.com/current")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("versions");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setExpires("PT0S");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("v1.2.3".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);
    operation.setClock(Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> operation.operate(Collections.emptyList()));

    assertEquals("Invalid expires value: PT0S", exception.getMessage());
  }

  @Test
  void shouldFallbackToAutoDetectWhenContentTypeIsNull() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("index.html")
            .reference("https://example.com/index.html")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));
    // contentType is null by default

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("<html/>".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertEquals("text/html", requestCaptor.getValue().contentType());
  }

  @Test
  void shouldFallbackToAutoDetectWhenContentTypeIsBlank() throws Exception {
    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("index.html")
            .reference("https://example.com/index.html")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setContentType("   ");
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact))
        .thenReturn(new ByteArrayInputStream("<html/>".getBytes(StandardCharsets.UTF_8)));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertEquals("text/html", requestCaptor.getValue().contentType());
  }

  @Test
  void shouldSkipUnchangedFilesWhenEtagsMatch() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"unchanged.txt", "changed.txt"},
            new String[] {"same content", "new content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Remote has unchanged.txt with matching ETag, changed.txt with different ETag
    String unchangedMd5 = md5Hex("same content");
    S3Object unchanged =
        S3Object.builder().key("prefix/unchanged.txt").eTag("\"" + unchangedMd5 + "\"").build();

    S3Object changed =
        S3Object.builder()
            .key("prefix/changed.txt")
            .eTag("\"oldmd5hash000000000000000000000\"")
            .build();

    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder().contents(unchanged, changed).isTruncated(false).build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(2, result.get("fileCount"));
    assertEquals(1, result.get("uploadCount"));
    assertEquals(1, result.get("skippedCount"));

    ArgumentCaptor<PutObjectRequest> uploaded = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(uploaded.capture(), any(RequestBody.class));
    assertTrue(uploaded.getValue().key().endsWith("changed.txt"));
  }

  @Test
  void shouldSkipAllFilesWhenNothingChanged() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "file2.txt"}, new String[] {"content1", "content2"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    S3Object obj1 =
        S3Object.builder().key("prefix/file1.txt").eTag("\"" + md5Hex("content1") + "\"").build();

    S3Object obj2 =
        S3Object.builder().key("prefix/file2.txt").eTag("\"" + md5Hex("content2") + "\"").build();

    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder().contents(obj1, obj2).isTruncated(false).build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(2, result.get("fileCount"));
    assertEquals(0, result.get("uploadCount"));
    assertEquals(2, result.get("skippedCount"));

    // TransferManager should not be invoked at all
    verify(amazonS3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void shouldUploadFileWhenRemoteHasMultipartEtag() throws Exception {
    byte[] zipContent = createTestZip(new String[] {"file.txt"}, new String[] {"content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Multipart ETags contain a dash suffix
    S3Object obj =
        S3Object.builder()
            .key("prefix/file.txt")
            .eTag("\"d41d8cd98f00b204e9800998ecf8427e-2\"")
            .build();

    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder().contents(obj).isTruncated(false).build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(1, result.get("uploadCount"));
    assertEquals(0, result.get("skippedCount"));

    verify(amazonS3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void shouldUploadNewFilesNotPresentInRemote() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"existing.txt", "brand-new.txt"},
            new String[] {"same content", "new content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Remote only has existing.txt with matching ETag
    S3Object existing =
        S3Object.builder()
            .key("prefix/existing.txt")
            .eTag("\"" + md5Hex("same content") + "\"")
            .build();

    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder().contents(existing).isTruncated(false).build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(2, result.get("fileCount"));
    assertEquals(1, result.get("uploadCount"));
    assertEquals(1, result.get("skippedCount"));

    ArgumentCaptor<PutObjectRequest> uploaded = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(amazonS3).putObject(uploaded.capture(), any(RequestBody.class));
    assertTrue(uploaded.getValue().key().endsWith("brand-new.txt"));
  }

  @Test
  void shouldHandlePaginatedEtagListing() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "file2.txt"}, new String[] {"content1", "content2"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Page 1: file1.txt with matching ETag
    S3Object obj1 =
        S3Object.builder().key("prefix/file1.txt").eTag("\"" + md5Hex("content1") + "\"").build();

    ListObjectsV2Response page1 =
        ListObjectsV2Response.builder()
            .contents(obj1)
            .isTruncated(true)
            .nextContinuationToken("token-123")
            .build();

    // Page 2: file2.txt with matching ETag
    S3Object obj2 =
        S3Object.builder().key("prefix/file2.txt").eTag("\"" + md5Hex("content2") + "\"").build();

    ListObjectsV2Response page2 =
        ListObjectsV2Response.builder().contents(obj2).isTruncated(false).build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(page1)
        .thenReturn(page2);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    // Both files matched across two pages — nothing should be uploaded
    assertEquals(2, result.get("fileCount"));
    assertEquals(0, result.get("uploadCount"));
    assertEquals(2, result.get("skippedCount"));

    verify(amazonS3, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
    verify(amazonS3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void shouldDeleteStaleFilesWhileSkippingUnchangedFiles() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"unchanged.txt", "changed.txt"},
            new String[] {"same content", "new content"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("prefix");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(true);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // ETag sync listing: unchanged.txt matches, changed.txt differs, stale.txt is extra
    S3Object unchanged =
        S3Object.builder()
            .key("prefix/unchanged.txt")
            .eTag("\"" + md5Hex("same content") + "\"")
            .build();

    S3Object changed =
        S3Object.builder()
            .key("prefix/changed.txt")
            .eTag("\"oldmd5hash000000000000000000000\"")
            .build();

    S3Object stale =
        S3Object.builder()
            .key("prefix/stale.txt")
            .eTag("\"someetag0000000000000000000000000\"")
            .build();

    ListObjectsV2Response listResult =
        ListObjectsV2Response.builder()
            .contents(unchanged, changed, stale)
            .isTruncated(false)
            .build();

    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(2, result.get("fileCount"));
    assertEquals(1, result.get("uploadCount"));
    assertEquals(1, result.get("skippedCount"));
    assertEquals(true, result.get("deleteStaleFiles"));
    assertEquals(1, result.get("deletedCount"));

    ArgumentCaptor<DeleteObjectsRequest> deleteCaptor =
        ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(amazonS3).deleteObjects(deleteCaptor.capture());

    List<String> deletedKeys =
        deleteCaptor.getValue().delete().objects().stream().map(ObjectIdentifier::key).toList();
    assertEquals(1, deletedKeys.size());
    assertTrue(deletedKeys.contains("prefix/stale.txt"));
  }

  @Test
  void shouldUploadAllFilesOnFirstDeploy() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "dir/file2.txt", "dir/nested/file3.txt"},
            new String[] {"content1", "content2", "content3"});

    Artifact artifact =
        Artifact.builder()
            .type("http/file")
            .name("test.zip")
            .reference("https://example.com/test.zip")
            .build();

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    when(artifactDownloader.download(artifact)).thenReturn(new ByteArrayInputStream(zipContent));

    // Empty remote (first deploy)
    ListObjectsV2Response emptyResult = ListObjectsV2Response.builder().isTruncated(false).build();
    when(amazonS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResult);

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(3, result.get("fileCount"));
    assertEquals(3, result.get("uploadCount"));
    assertEquals(0, result.get("skippedCount"));

    verify(amazonS3, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  private static String md5Hex(String content) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
