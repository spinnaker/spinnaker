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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.UploadArtifactToS3AtomicOperation;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UploadArtifactToS3AtomicOperationConverterTest {

  private UploadArtifactToS3AtomicOperationConverter converter;
  private NetflixAmazonCredentials mockCredentials;

  @BeforeEach
  void setUp() {
    converter =
        new UploadArtifactToS3AtomicOperationConverter(
            mock(AmazonClientProvider.class), mock(ArtifactDownloader.class));
    converter.setObjectMapper(new ObjectMapper());

    AccountCredentialsProvider accountCredentialsProvider = mock(AccountCredentialsProvider.class);
    mockCredentials = mock(NetflixAmazonCredentials.class);
    when(accountCredentialsProvider.getCredentials(anyString())).thenReturn(mockCredentials);
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
  }

  @Test
  void shouldConvertDescriptionWithAllFields() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("objectKeyPrefix", "releases/v1");
    input.put("credentials", "test-account");
    input.put(
        "artifact",
        Map.of(
            "type", "http/file", "name", "test.zip", "reference", "https://example.com/test.zip"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertEquals("us-west-2", description.getRegion());
    assertEquals("my-bucket", description.getBucket());
    assertEquals("releases/v1", description.getObjectKeyPrefix());
    assertNotNull(description.getArtifact());
    assertEquals("test.zip", description.getArtifact().getName());
    assertSame(mockCredentials, description.getCredentials());
  }

  @Test
  void shouldConvertOperation() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/test.zip"));

    var operation = converter.convertOperation(input);

    assertInstanceOf(UploadArtifactToS3AtomicOperation.class, operation);
  }

  @Test
  void shouldConvertExtractArchiveField() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("extractArchive", false);
    input.put(
        "artifact", Map.of("type", "http/file", "reference", "https://example.com/config.json"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertFalse(description.isExtractArchive());
  }

  @Test
  void shouldDefaultExtractArchiveToTrue() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/test.zip"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertTrue(description.isExtractArchive());
  }

  @Test
  void shouldConvertDeleteStaleFilesField() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("deleteStaleFiles", true);
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/test.zip"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertTrue(description.isDeleteStaleFiles());
  }

  @Test
  void shouldConvertCacheControlField() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("cacheControl", "max-age=31536000, public");
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/test.zip"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertEquals("max-age=31536000, public", description.getCacheControl());
  }

  @Test
  void shouldDefaultCacheControlToNull() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/test.zip"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertNull(description.getCacheControl());
  }

  @Test
  void shouldConvertContentTypeField() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("contentType", "text/plain");
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/current"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertEquals("text/plain", description.getContentType());
  }

  @Test
  void shouldDefaultContentTypeToNull() {
    Map<String, Object> input = new HashMap<>();
    input.put("region", "us-west-2");
    input.put("bucket", "my-bucket");
    input.put("credentials", "test-account");
    input.put("artifact", Map.of("type", "http/file", "reference", "https://example.com/test.zip"));

    UploadArtifactToS3Description description = converter.convertDescription(input);

    assertNull(description.getContentType());
  }
}
