/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.s3.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.aws.security.AwsNamedAccountCredentials;
import com.netflix.kayenta.index.CanaryConfigIndex;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
class S3StorageServiceIntegrationTest {

  private static final String MINIO_IMAGE = "minio/minio:RELEASE.2023-09-04T19-57-37Z";
  private static final String ACCOUNT_NAME = "test-account";
  private static final String BUCKET = "kayenta-test";
  private static final String ROOT_FOLDER = "kayenta";

  @Container static final MinIOContainer minio = new MinIOContainer(MINIO_IMAGE);

  private static S3Client s3Client;
  private static ObjectMapper objectMapper;

  private S3StorageService storageService;
  private AccountCredentialsRepository credentialsRepository;

  @BeforeAll
  static void setUpOnce() {
    s3Client =
        S3Client.builder()
            .endpointOverride(URI.create(minio.getS3URL()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
            .region(Region.US_EAST_1)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();

    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

    objectMapper = new ObjectMapper();
  }

  @BeforeEach
  void setUp() {
    AwsNamedAccountCredentials credentials =
        AwsNamedAccountCredentials.builder()
            .name(ACCOUNT_NAME)
            .credentials(new com.netflix.kayenta.aws.security.AwsCredentials())
            .bucket(BUCKET)
            .region("us-east-1")
            .rootFolder(ROOT_FOLDER)
            .s3Client(s3Client)
            .build();

    credentialsRepository = new MapBackedAccountCredentialsRepository();
    credentialsRepository.save(ACCOUNT_NAME, credentials);

    CanaryConfigIndex canaryConfigIndex = mock(CanaryConfigIndex.class);
    when(canaryConfigIndex.getRedisTime()).thenReturn(System.currentTimeMillis());

    storageService =
        S3StorageService.builder().objectMapper(objectMapper).accountName(ACCOUNT_NAME).build();

    // inject dependencies via reflection since @Builder + @Autowired don't mix in tests
    try {
      var repoField = S3StorageService.class.getDeclaredField("accountCredentialsRepository");
      repoField.setAccessible(true);
      repoField.set(storageService, credentialsRepository);

      var indexField = S3StorageService.class.getDeclaredField("canaryConfigIndex");
      indexField.setAccessible(true);
      indexField.set(storageService, canaryConfigIndex);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void storeAndLoadMetricSetList() throws Exception {
    String objectKey = "test-metric-set-" + System.currentTimeMillis();

    var metricSet = com.netflix.kayenta.metrics.MetricSet.builder().name("test-metric").build();
    List<com.netflix.kayenta.metrics.MetricSet> stored = List.of(metricSet);

    storageService.storeObject(
        ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, objectKey, stored, null, false);

    List<com.netflix.kayenta.metrics.MetricSet> loaded =
        storageService.loadObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, objectKey);

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get(0).getName()).isEqualTo("test-metric");
  }

  @Test
  void listObjectKeys() {
    String objectKey = "list-test-" + System.currentTimeMillis();

    var metricSet = com.netflix.kayenta.metrics.MetricSet.builder().name("list-metric").build();

    storageService.storeObject(
        ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, objectKey, List.of(metricSet), null, false);

    List<Map<String, Object>> keys =
        storageService.listObjectKeys(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, List.of(), true);

    assertThat(keys).extracting(m -> m.get("id")).contains(objectKey);
  }

  @Test
  void deleteObject() {
    String objectKey = "delete-test-" + System.currentTimeMillis();

    var metricSet = com.netflix.kayenta.metrics.MetricSet.builder().name("to-delete").build();

    storageService.storeObject(
        ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, objectKey, List.of(metricSet), null, false);

    storageService.deleteObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, objectKey);

    assertThatThrownBy(
            () -> storageService.loadObject(ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, objectKey))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void loadMissingObjectThrowsNotFoundException() {
    assertThatThrownBy(
            () ->
                storageService.loadObject(
                    ACCOUNT_NAME, ObjectType.METRIC_SET_LIST, "does-not-exist"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void ensureBucketExistsIsIdempotent() {
    // bucket was created in @BeforeAll — calling again should not throw
    storageService.ensureBucketExists(ACCOUNT_NAME);
  }
}
