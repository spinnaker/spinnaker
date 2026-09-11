/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3StaticDataProviderConfiguration.StaticRecord;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3StaticDataProviderConfiguration.StaticRecordType;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Validates that {@link AmazonS3DataProvider}'s AWS SDK v2 {@code fetchObject()} call (added by the
 * S3 v1-&gt;v2 migration) actually round-trips real bytes against an S3-compatible API, exercising
 * the real {@link software.amazon.awssdk.services.s3.model.GetObjectRequest} construction and
 * {@link ResponseInputStream} consumption end-to-end through {@link
 * AmazonS3DataProvider#getStaticData} -- not just mocked interactions.
 *
 * <p>Uses testcontainers' MinIO module (same pattern as {@code
 * kayenta-s3/S3StorageServiceIntegrationTest}) rather than LocalStack -- MinIO is a plain
 * S3-compatible object server with no Lambda/ECS docker-in-docker machinery, so it needs no host
 * docker.sock bind-mount and starts reliably across Docker runtimes (including Colima).
 */
@Testcontainers
class AmazonS3DataProviderMinioTest {

  private static final String MINIO_IMAGE = "minio/minio:RELEASE.2023-09-04T19-57-37Z";
  private static final String BUCKET_NAME = "s3-migration-test-bucket";
  private static final String ACCOUNT_NAME = "test";
  private static final String REGION = "us-east-1";

  @Container static final MinIOContainer minio = new MinIOContainer(MINIO_IMAGE);

  private static S3Client s3Client;
  private static AmazonS3DataProvider dataProvider;

  @BeforeAll
  static void setupOnce() {
    s3Client =
        S3Client.builder()
            .endpointOverride(URI.create(minio.getS3URL()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
            .region(Region.of(REGION))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();

    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    putString("string-key", "hello from minio s3");
    putString("object-key", "{\"foo\":\"bar\"}");
    putString("list-key", "[{\"name\":\"a\"},{\"name\":\"b\"}]");

    NetflixAmazonCredentials credentials =
        new ObjectMapper()
            .convertValue(
                Map.of(
                    "name", ACCOUNT_NAME,
                    "environment", ACCOUNT_NAME,
                    "accountType", ACCOUNT_NAME,
                    "accountId", "123456789012",
                    "regions", List.of(Map.of("name", REGION, "availabilityZones", List.of()))),
                NetflixAmazonCredentials.class);

    AmazonClientProvider mockAmazonClientProvider = mock(AmazonClientProvider.class);
    when(mockAmazonClientProvider.getAmazonS3V2(eq(credentials), any())).thenReturn(s3Client);

    @SuppressWarnings("unchecked")
    CredentialsRepository<NetflixAmazonCredentials> mockCredentialsRepository =
        mock(CredentialsRepository.class);
    when(mockCredentialsRepository.getOne(ACCOUNT_NAME)).thenReturn(credentials);

    AmazonS3StaticDataProviderConfiguration configuration =
        new AmazonS3StaticDataProviderConfiguration(
            List.of(
                staticRecord("stringRecordId", StaticRecordType.string, "string-key"),
                staticRecord("objectRecordId", StaticRecordType.object, "object-key"),
                staticRecord("listRecordId", StaticRecordType.list, "list-key")),
            Collections.emptyList());

    dataProvider =
        new AmazonS3DataProvider(
            new ObjectMapper(), mockAmazonClientProvider, mockCredentialsRepository, configuration);
  }

  private static void putString(String key, String contents) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(BUCKET_NAME).key(key).build(),
        RequestBody.fromString(contents, StandardCharsets.UTF_8));
  }

  private static StaticRecord staticRecord(String id, StaticRecordType type, String key) {
    return new StaticRecord(id, type, ACCOUNT_NAME, REGION, BUCKET_NAME, key);
  }

  @Test
  void fetchObjectRoundTripsRealBytesThroughAwsSdkV2() throws Exception {
    try (ResponseInputStream<GetObjectResponse> s3Object =
        dataProvider.fetchObject(ACCOUNT_NAME, REGION, BUCKET_NAME, "string-key")) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      s3Object.transferTo(outputStream);
      assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("hello from minio s3");
    }
  }

  @Test
  void getStaticDataReturnsRawStringForStringRecords() {
    Object result = dataProvider.getStaticData("stringRecordId", Map.of());
    assertThat(result).isEqualTo("hello from minio s3");
  }

  @Test
  void getStaticDataParsesJsonObjectForObjectRecords() {
    Object result = dataProvider.getStaticData("objectRecordId", Map.of());
    assertThat(result).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) result).get("foo")).isEqualTo("bar");
  }

  @Test
  void getStaticDataParsesAndFiltersJsonListForListRecords() {
    Object result = dataProvider.getStaticData("listRecordId", Map.of("name", "b"));
    assertThat(result).isInstanceOf(List.class);
    List<?> list = (List<?>) result;
    assertThat(list).hasSize(1);
    assertThat(((Map<?, ?>) list.get(0)).get("name")).isEqualTo("b");
  }
}
