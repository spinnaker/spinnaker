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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.aws2.SpectatorExecutionInterceptor;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {S3ArtifactCredentialsTest.TestConfiguration.class})
class S3ArtifactCredentialsTest {

  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(S3ArtifactCredentialsTest.class);

  private static DockerImageName localstackImage =
      DockerImageName.parse(
          "localstack/localstack:0.12.18"); // 0.12.18 is the latest as of 1-oct-21

  private static LocalStackContainer localstack =
      DockerClientFactory.instance().isDockerAvailable()
          ? new LocalStackContainer(localstackImage).withServices(S3)
          : null;

  private static S3Client s3Client;

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

  static Registry registry = new DefaultRegistry();

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    localstack.start();
    s3Client =
        S3Client.builder()
            .endpointOverride(URI.create(localstack.getEndpoint().toString()))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .forcePathStyle(true)
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .addExecutionInterceptor(
                        new S3ArtifactCredentials.S3ArtifactRequestInterceptor(
                            "my-s3-account", new S3ArtifactProviderProperties()))
                    .addExecutionInterceptor(new SpectatorExecutionInterceptor(registry))
                    .build())
            .build();

    // Create a bucket so there's a place to retrieve from
    s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());

    // Create a file so there's something to download
    s3Client.putObject(
        PutObjectRequest.builder().bucket(BUCKET_NAME).key(KEY_NAME).build(),
        RequestBody.fromString(CONTENTS));
  }

  @Test
  void normalDownload() throws IOException {
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, s3Client, s3ArtifactProviderProperties, registry);
    try (InputStream artifactStream = s3ArtifactCredentials.download(artifact)) {
      String actual = new String(artifactStream.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(CONTENTS, actual);
    }

    // Verify that metrics were reported
    assertThat(registry.counters()).hasSize(3);
    Counter counter = registry.counters().findFirst().orElseThrow(AssertionError::new);
    assertThat(counter.id().name()).isEqualTo("ipc.client.call");
    assertThat(counter.id().tags()).contains(Tag.of("http.status", "200"));
    assertThat(counter.actualCount()).isEqualTo(1);
    assertThat(registry.timers()).hasSize(3);
  }

  @Test
  void normalDownloadWithValidation() throws IOException {
    S3ArtifactValidator s3ArtifactValidator = spy(DummyS3ArtifactValidator.class);
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(
            account,
            Optional.of(s3ArtifactValidator),
            s3Client,
            s3ArtifactProviderProperties,
            registry);
    try (InputStream artifactStream = s3ArtifactCredentials.download(artifact)) {
      String actual = new String(artifactStream.readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(CONTENTS, actual);
    }

    // Verify validator was called with correct parameters
    verify(s3ArtifactValidator)
        .validate(eq(s3Client), eq(BUCKET_NAME), eq(KEY_NAME), any(InputStream.class));
  }

  @Test
  void invalidReference() {
    Artifact otherArtifact =
        Artifact.builder().name("invalid-reference").reference("no-s3-prefix").build();
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, s3Client, s3ArtifactProviderProperties, registry);
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
        new S3ArtifactCredentials(account, s3Client, s3ArtifactProviderProperties, registry);
    assertThatThrownBy(() -> s3ArtifactCredentials.download(otherArtifact))
        .isInstanceOf(NoSuchBucketException.class)
        .hasMessageContaining("The specified bucket does not exist");
  }

  @Test
  void fileNotFound() {
    String bucketName = "s3://" + BUCKET_NAME + "/does-not-exist";
    Artifact otherArtifact =
        Artifact.builder().name("file-not-found-artifact").reference(bucketName).build();
    S3ArtifactCredentials s3ArtifactCredentials =
        new S3ArtifactCredentials(account, s3Client, s3ArtifactProviderProperties, registry);
    assertThatThrownBy(() -> s3ArtifactCredentials.download(otherArtifact))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(bucketName + " not found");
  }

  // If S3ArtifactCredentials.S3ArtifactRequestHandler uses, say a HashSet
  // instead of ConcurrentHashMap.newKeySet(), this test only fails sometimes,
  // even with 1000 repetitions.  Each iteration takes less than 100 msec on my
  // machine, but leaving with 10 iterations to keep from increasing test times
  // unnecessarily.
  @RepeatedTest(10)
  void endpointLoggingIsThreadSafe() throws Exception {

    // Capture the log messages that S3ArtifactCredentials generates
    Logger logger = (Logger) LoggerFactory.getLogger(S3ArtifactCredentials.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();

    s3ArtifactProviderProperties.setLogEndpoints(true);
    S3ArtifactCredentials.S3ArtifactRequestInterceptor s3ArtifactRequestInterceptor =
        new S3ArtifactCredentials.S3ArtifactRequestInterceptor(
            "test", s3ArtifactProviderProperties);

    // Use the same endpoint in all the threads to exercise both the map-level
    // operations, as well as the set operations.
    Context.BeforeTransmission context = mock(Context.BeforeTransmission.class);
    SdkHttpRequest request = mock(SdkHttpRequest.class);
    URI uri = new URI("https://example.com");
    when(context.httpRequest()).thenReturn(request);
    when(request.getUri()).thenReturn(uri);

    ExecutionAttributes executionAttributes = new ExecutionAttributes();

    int numberOfThreads = 100; // arbitrary
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    final ArrayList<Future<Exception>> futures = new ArrayList<>(numberOfThreads);
    for (int i = 0; i < numberOfThreads; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  s3ArtifactRequestInterceptor.beforeTransmission(context, executionAttributes);
                  return null;
                } catch (Exception e) {
                  log.error("exception in s3ArtifactRequestInterceptor.beforeTransmission", e);
                  // Return the exception as a way to communicate it to the caller
                  // since throwing, by itself, doesn't.
                  return e;
                }
              }));
    }

    // Make sure none of the threads returned an exception
    for (Future<Exception> future : futures)
      assertNull(future.get(5, TimeUnit.SECONDS)); // arbitrary timeout

    // No matter how many threads there are, since there's one endpoint, expect
    // one log message.
    assertEquals(1, listAppender.list.size());
  }

  static class DummyS3ArtifactValidator implements S3ArtifactValidator {
    @Override
    public InputStream validate(
        S3Client s3Client, String bucketName, String key, InputStream objectContent) {
      return objectContent;
    }
  }

  static class TestConfiguration {
    @Bean
    Registry registry() {
      return new DefaultRegistry();
    }
  }
}
