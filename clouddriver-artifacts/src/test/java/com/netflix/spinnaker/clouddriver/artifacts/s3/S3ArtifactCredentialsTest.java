/*
 * Copyright 2021 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Functions;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.aws.SpectatorRequestMetricCollector;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.aws.AwsComponents;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@ExtendWith(SpringExtension.class)
// Include AwsComponents for the SpectatorMetricCollector bean
@ContextConfiguration(
    classes = {AwsComponents.class, S3ArtifactCredentialsTest.TestConfiguration.class})
class S3ArtifactCredentialsTest {

  private static DockerImageName localstackImage =
      DockerImageName.parse(
          "localstack/localstack:0.12.18"); // 0.12.18 is the latest as of 1-oct-21

  private static LocalStackContainer localstack =
      DockerClientFactory.instance().isDockerAvailable()
          ? new LocalStackContainer(localstackImage).withServices(S3)
          : null;

  private static AmazonS3 amazonS3;

  private static final S3ArtifactAccount account =
      S3ArtifactAccount.builder().name("my-s3-account").build();

  private static final String BUCKET_NAME = "my-bucket";

  private static final String KEY_NAME = "my-file";

  private static final String CONTENTS = "arbitrary file contents";

  private final Artifact artifact =
      Artifact.builder()
          .name("my-s3-artifact")
          .reference("s3://" + BUCKET_NAME + "/" + KEY_NAME)
          .build();

  private final S3ArtifactProviderProperties s3ArtifactProviderProperties =
      new S3ArtifactProviderProperties();

  @Autowired Registry registry;

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    localstack.start();
    amazonS3 =
        AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    localstack.getEndpoint().toString(), localstack.getRegion()))
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .withRequestHandlers(
                new S3ArtifactCredentials.S3ArtifactRequestHandler(account.getName()))
            .build();

    // Create a bucket so there's a place to retrieve from
    amazonS3.createBucket(BUCKET_NAME);

    // Create a file so there's something to download
    amazonS3.putObject(BUCKET_NAME, KEY_NAME, CONTENTS);
  }

  @Test
  void normalDownload() throws IOException {
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, amazonS3, s3ArtifactProviderProperties);
    try (InputStream artifactStream = s3ArtifactCredentials.download(artifact)) {
      String actual = new String(artifactStream.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(CONTENTS, actual);
    }

    // Verify that metrics were reported
    assertThat(registry.counters()).hasSize(1);
    Counter counter = registry.counters().findFirst().orElseThrow(AssertionError::new);
    assertThat(counter.id().name()).isEqualTo("aws.request.requestCount");
    assertThat(counter.id().tags()).contains(Tag.of("serviceName", "Amazon S3"));
    assertThat(counter.actualCount()).isEqualTo(1);
    assertThat(registry.gauges()).hasSize(3);

    // Verify that the tag that comes from the request handler is present on an
    // arbitrary gauge
    Gauge gauge =
        registry
            .gauges()
            .filter(Functions.nameEquals("aws.request.httpClientPoolAvailableCount"))
            .findFirst()
            .orElseThrow(AssertionError::new);
    assertThat(gauge.id().tags())
        .contains(
            Tag.of(
                SpectatorRequestMetricCollector.DEFAULT_HANDLER_CONTEXT_KEY.getName(),
                account.getName()));
  }

  @Test
  void normalDownloadWithValidation() throws IOException {
    S3ArtifactValidator s3ArtifactValidator = spy(DummyS3ArtifactValidator.class);
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(
            account, Optional.of(s3ArtifactValidator), amazonS3, s3ArtifactProviderProperties);
    try (InputStream artifactStream = s3ArtifactCredentials.download(artifact)) {
      String actual = new String(artifactStream.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(CONTENTS, actual);
    }

    ArgumentCaptor<S3Object> s3ObjectCaptor = ArgumentCaptor.forClass(S3Object.class);
    verify(s3ArtifactValidator).validate(eq(amazonS3), s3ObjectCaptor.capture());

    assertEquals(BUCKET_NAME, s3ObjectCaptor.getValue().getBucketName());
    assertEquals(KEY_NAME, s3ObjectCaptor.getValue().getKey());
  }

  @Test
  void invalidReference() {
    Artifact otherArtifact =
        Artifact.builder().name("invalid-reference").reference("no-s3-prefix").build();
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, amazonS3, s3ArtifactProviderProperties);
    assertThatThrownBy(() -> s3ArtifactCredentials.download(otherArtifact))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bucketDoesNotExist() {
    Artifact otherArtifact =
        Artifact.builder()
            .name("does-not-exist-artifact")
            .reference("s3://does-not-exist/foo")
            .build();
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, amazonS3, s3ArtifactProviderProperties);
    assertThatThrownBy(() -> s3ArtifactCredentials.download(otherArtifact))
        .isInstanceOf(AmazonS3Exception.class)
        .hasMessageContaining("The specified bucket does not exist");
  }

  @Test
  void fileNotFound() {
    String bucketName = "s3://" + BUCKET_NAME + "/does-not-exist";
    Artifact otherArtifact =
        Artifact.builder().name("file-not-found-artifact").reference(bucketName).build();
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, amazonS3, s3ArtifactProviderProperties);
    assertThatThrownBy(() -> s3ArtifactCredentials.download(otherArtifact))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(bucketName + " not found");
  }

  static class DummyS3ArtifactValidator implements S3ArtifactValidator {
    @Override
    public InputStream validate(AmazonS3 amazonS3, S3Object s3obj) {
      return s3obj.getObjectContent();
    }
  }

  static class TestConfiguration {
    @Bean
    Registry registry() {
      return new DefaultRegistry();
    }
  }
}
