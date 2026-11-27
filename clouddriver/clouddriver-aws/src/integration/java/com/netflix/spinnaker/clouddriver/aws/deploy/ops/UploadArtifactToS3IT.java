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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.aws.TestCredential;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ministack.testcontainers.MiniStackContainer;
import org.mockito.Mockito;
import org.testcontainers.DockerClientFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

class UploadArtifactToS3IT {

  private static final MiniStackContainer localstack =
      DockerClientFactory.instance().isDockerAvailable() ? new MiniStackContainer("1.5.10") : null;

  private static final String BUCKET_NAME = "test-upload-bucket";

  private static S3Client amazonS3;

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    localstack.start();

    amazonS3 =
        S3Client.builder()
            .endpointOverride(URI.create(localstack.getEndpoint()))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .forcePathStyle(true)
            .build();

    amazonS3.createBucket(b -> b.bucket(BUCKET_NAME));
  }

  @BeforeEach
  void setUp() {
    Task task = Mockito.mock(Task.class);
    TaskRepository.threadLocalTask.set(task);

    // Clean bucket before each test
    List<S3Object> objects = amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents();
    for (S3Object obj : objects) {
      amazonS3.deleteObject(b -> b.bucket(BUCKET_NAME).key(obj.key()));
    }
  }

  @Test
  void shouldUploadExtractedZipFilesToS3() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"file1.txt", "dir/file2.txt", "dir/nested/file3.txt"},
            new String[] {"content1", "content2", "content3"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("test.zip").reference("test.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals("s3://test-upload-bucket/releases/v1/", result.get("destinationUri"));
    assertEquals(3, result.get("fileCount"));

    List<String> uploadedKeys =
        amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .sorted()
            .toList();

    assertEquals(3, uploadedKeys.size());
    assertTrue(uploadedKeys.contains("releases/v1/file1.txt"));
    assertTrue(uploadedKeys.contains("releases/v1/dir/file2.txt"));
    assertTrue(uploadedKeys.contains("releases/v1/dir/nested/file3.txt"));

    assertEquals("content1", getObjectAsString("releases/v1/file1.txt"));
    assertEquals("content2", getObjectAsString("releases/v1/dir/file2.txt"));
    assertEquals("content3", getObjectAsString("releases/v1/dir/nested/file3.txt"));
  }

  @Test
  void shouldUploadToRootWhenNoPrefixSpecified() throws Exception {
    byte[] zipContent =
        createTestZip(new String[] {"app.js", "index.html"}, new String[] {"js", "html"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("app.zip").reference("app.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals("s3://test-upload-bucket/", result.get("destinationUri"));
    assertEquals(2, result.get("fileCount"));

    List<String> uploadedKeys =
        amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .sorted()
            .toList();

    assertTrue(uploadedKeys.contains("app.js"));
    assertTrue(uploadedKeys.contains("index.html"));
  }

  @Test
  void shouldOverwriteExistingFiles() throws Exception {
    amazonS3.putObject(
        b -> b.bucket(BUCKET_NAME).key("releases/v1/file1.txt"),
        RequestBody.fromString("old content"));

    byte[] zipContent = createTestZip(new String[] {"file1.txt"}, new String[] {"new content"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("test.zip").reference("test.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    assertEquals("new content", getObjectAsString("releases/v1/file1.txt"));
  }

  private String getObjectAsString(String key) {
    return amazonS3.getObjectAsBytes(b -> b.bucket(BUCKET_NAME).key(key)).asUtf8String();
  }

  @Test
  void shouldDeleteStaleFilesWhenDeleteStaleFilesIsTrue() throws Exception {
    // Pre-populate S3 with stale files
    putObject("releases/v1/old-file.txt", "stale content");
    putObject("releases/v1/another-old.txt", "stale content 2");

    byte[] zipContent =
        createTestZip(new String[] {"file1.txt", "dir/file2.txt"}, new String[] {"new1", "new2"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("test.zip").reference("test.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(true);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(true, result.get("deleteStaleFiles"));
    assertEquals(2, result.get("deletedCount"));

    List<String> remainingKeys =
        amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .sorted()
            .toList();

    assertEquals(2, remainingKeys.size());
    assertTrue(remainingKeys.contains("releases/v1/file1.txt"));
    assertTrue(remainingKeys.contains("releases/v1/dir/file2.txt"));
    assertFalse(remainingKeys.contains("releases/v1/old-file.txt"));
    assertFalse(remainingKeys.contains("releases/v1/another-old.txt"));
  }

  @Test
  void shouldPreserveStaleFilesWhenDeleteStaleFilesIsFalse() throws Exception {
    // Pre-populate S3 with stale files
    putObject("releases/v1/old-file.txt", "stale content");

    byte[] zipContent = createTestZip(new String[] {"file1.txt"}, new String[] {"new content"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("test.zip").reference("test.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(false);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(false, result.get("deleteStaleFiles"));
    assertEquals(0, result.get("deletedCount"));

    List<String> remainingKeys =
        amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .sorted()
            .toList();

    // Both the new file and the stale file should still exist
    assertTrue(remainingKeys.contains("releases/v1/file1.txt"));
    assertTrue(remainingKeys.contains("releases/v1/old-file.txt"));
  }

  @Test
  void shouldDeleteStaleDirectoryTreeWhenDeleteStaleFilesIsTrue() throws Exception {
    // Pre-populate S3 with a stale directory tree
    putObject("releases/v1/old-module/css/style.css", "old css");
    putObject("releases/v1/old-module/js/app.js", "old js");
    putObject("releases/v1/old-module/index.html", "old html");
    // Also put a file that will be in the new artifact
    putObject("releases/v1/index.html", "will be overwritten");

    byte[] zipContent =
        createTestZip(
            new String[] {"index.html", "css/main.css"}, new String[] {"new html", "new css"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("test.zip").reference("test.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("releases/v1");
    description.setArtifact(artifact);
    description.setDeleteStaleFiles(true);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(true, result.get("deleteStaleFiles"));
    assertEquals(3, result.get("deletedCount"));

    List<String> remainingKeys =
        amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .sorted()
            .toList();

    // Only the new artifact files should remain
    assertEquals(2, remainingKeys.size());
    assertTrue(remainingKeys.contains("releases/v1/index.html"));
    assertTrue(remainingKeys.contains("releases/v1/css/main.css"));

    // Stale directory tree should be gone
    assertFalse(remainingKeys.contains("releases/v1/old-module/css/style.css"));
    assertFalse(remainingKeys.contains("releases/v1/old-module/js/app.js"));
    assertFalse(remainingKeys.contains("releases/v1/old-module/index.html"));

    // Verify content of remaining files
    assertEquals("new html", getObjectAsString("releases/v1/index.html"));
    assertEquals("new css", getObjectAsString("releases/v1/css/main.css"));
  }

  private void putObject(String key, String content) {
    amazonS3.putObject(b -> b.bucket(BUCKET_NAME).key(key), RequestBody.fromString(content));
  }

  @Test
  void shouldUploadSingleFileWithoutExtraction() throws Exception {
    String fileContent = "{\"config\": \"value\", \"version\": 42}";

    Artifact artifact =
        Artifact.builder()
            .type("embedded/base64")
            .name("config.json")
            .reference("config.json")
            .build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("configs/production");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals(
        "s3://test-upload-bucket/configs/production/config.json", result.get("destinationUri"));
    assertEquals(1, result.get("fileCount"));
    assertEquals(false, result.get("deleteStaleFiles"));
    assertEquals(0, result.get("deletedCount"));

    List<String> uploadedKeys =
        amazonS3.listObjectsV2(b -> b.bucket(BUCKET_NAME)).contents().stream()
            .map(S3Object::key)
            .toList();

    assertEquals(1, uploadedKeys.size());
    assertTrue(uploadedKeys.contains("configs/production/config.json"));

    String actualContent = getObjectAsString("configs/production/config.json");
    assertEquals(fileContent, actualContent);
  }

  @Test
  void shouldUploadSingleFileToRootWhenNoPrefixSpecified() throws Exception {
    String fileContent = "key: value";

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("data.yaml").reference("data.yaml").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals("s3://test-upload-bucket/data.yaml", result.get("destinationUri"));
    assertEquals(1, result.get("fileCount"));

    String actualContent = getObjectAsString("data.yaml");
    assertEquals(fileContent, actualContent);
  }

  @Test
  void shouldSetContentTypeOnUploadedFiles() throws Exception {
    byte[] zipContent =
        createTestZip(
            new String[] {"index.html", "style.css", "app.js", "data.unknown"},
            new String[] {"<html/>", "body{}", "var x=1;", "binary"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("site.zip").reference("site.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    var htmlMeta = amazonS3.headObject(b -> b.bucket(BUCKET_NAME).key("site/index.html"));
    assertEquals("text/html", htmlMeta.contentType());

    var unknownMeta = amazonS3.headObject(b -> b.bucket(BUCKET_NAME).key("site/data.unknown"));
    assertEquals("application/octet-stream", unknownMeta.contentType());
  }

  @Test
  void shouldSetCacheControlOnUploadedFiles() throws Exception {
    byte[] zipContent =
        createTestZip(new String[] {"index.html", "app.js"}, new String[] {"<html/>", "var x=1;"});

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("site.zip").reference("site.zip").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(zipContent));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("site");
    description.setArtifact(artifact);
    description.setCacheControl("max-age=31536000, public");
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    var htmlMeta = amazonS3.headObject(b -> b.bucket(BUCKET_NAME).key("site/index.html"));
    assertEquals("max-age=31536000, public", htmlMeta.cacheControl());

    var jsMeta = amazonS3.headObject(b -> b.bucket(BUCKET_NAME).key("site/app.js"));
    assertEquals("max-age=31536000, public", jsMeta.cacheControl());
  }

  @Test
  void shouldSetContentTypeOnSingleFileUpload() throws Exception {
    String fileContent = "<html><body>Hello</body></html>";

    Artifact artifact =
        Artifact.builder()
            .type("embedded/base64")
            .name("index.html")
            .reference("index.html")
            .build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("pages");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setCacheControl("no-cache");
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    operation.operate(Collections.emptyList());

    var meta = amazonS3.headObject(b -> b.bucket(BUCKET_NAME).key("pages/index.html"));
    assertEquals("text/html", meta.contentType());
    assertEquals("no-cache", meta.cacheControl());
  }

  @Test
  void shouldUseContentTypeOverrideOnSingleFileUpload() throws Exception {
    String fileContent = "v1.2.3";

    Artifact artifact =
        Artifact.builder().type("embedded/base64").name("current").reference("current").build();

    ArtifactDownloader artifactDownloader = Mockito.mock(ArtifactDownloader.class);
    Mockito.when(artifactDownloader.download(Mockito.any(Artifact.class)))
        .thenReturn(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

    AmazonClientProvider amazonClientProvider = Mockito.mock(AmazonClientProvider.class);
    Mockito.when(
            amazonClientProvider.getAmazonS3V2(
                Mockito.any(NetflixAmazonCredentials.class), Mockito.anyString()))
        .thenReturn(amazonS3);

    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-east-1");
    description.setBucket(BUCKET_NAME);
    description.setObjectKeyPrefix("versions");
    description.setArtifact(artifact);
    description.setExtractArchive(false);
    description.setContentType("text/plain");
    description.setCredentials(TestCredential.named("test"));

    UploadArtifactToS3AtomicOperation operation =
        new UploadArtifactToS3AtomicOperation(
            description, amazonClientProvider, artifactDownloader);

    Map<String, Object> result = operation.operate(Collections.emptyList());

    assertEquals("s3://test-upload-bucket/versions/current", result.get("destinationUri"));
    assertEquals(1, result.get("fileCount"));

    var meta = amazonS3.headObject(b -> b.bucket(BUCKET_NAME).key("versions/current"));
    assertEquals("text/plain", meta.contentType());

    String actualContent = getObjectAsString("versions/current");
    assertEquals(fileContent, actualContent);
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
