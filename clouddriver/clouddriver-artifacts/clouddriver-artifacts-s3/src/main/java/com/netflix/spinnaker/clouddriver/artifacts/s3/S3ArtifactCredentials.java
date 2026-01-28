/*
 * Copyright 2018 Datadog, Inc.
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

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.aws2.SpectatorExecutionInterceptor;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@NonnullByDefault
public class S3ArtifactCredentials implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-s3";
  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of("s3/object");

  private final String apiEndpoint;
  private final String apiRegion;
  private final String region;
  private final String awsAccessKeyId;
  private final String awsSecretAccessKey;
  private final Optional<S3ArtifactValidator> s3ArtifactValidator;
  private final S3ArtifactProviderProperties s3ArtifactProviderProperties;
  private final S3ArtifactRequestInterceptor s3ArtifactRequestInterceptor;
  private final SpectatorExecutionInterceptor spectatorExecutionInterceptor;

  private S3Client s3Client;

  S3ArtifactCredentials(
      S3ArtifactAccount account,
      Optional<S3ArtifactValidator> s3ArtifactValidator,
      S3ArtifactProviderProperties s3ArtifactProviderProperties,
      Registry registry) {
    this(account, s3ArtifactValidator, null, s3ArtifactProviderProperties, registry);
  }

  S3ArtifactCredentials(
      S3ArtifactAccount account,
      @Nullable S3Client s3Client,
      S3ArtifactProviderProperties s3ArtifactProviderProperties,
      Registry registry) {
    this(account, Optional.empty(), s3Client, s3ArtifactProviderProperties, registry);
  }

  S3ArtifactCredentials(
      S3ArtifactAccount account,
      Optional<S3ArtifactValidator> s3ArtifactValidator,
      @Nullable S3Client s3Client,
      S3ArtifactProviderProperties s3ArtifactProviderProperties,
      Registry registry)
      throws IllegalArgumentException {
    name = account.getName();
    apiEndpoint = account.getApiEndpoint();
    apiRegion = account.getApiRegion();
    region = account.getRegion();
    awsAccessKeyId = account.getAwsAccessKeyId();
    awsSecretAccessKey = account.getAwsSecretAccessKey();
    this.s3ArtifactValidator = s3ArtifactValidator;
    this.s3Client = s3Client;
    this.s3ArtifactProviderProperties = s3ArtifactProviderProperties;
    s3ArtifactRequestInterceptor =
        new S3ArtifactRequestInterceptor(name, this.s3ArtifactProviderProperties);
    spectatorExecutionInterceptor = new SpectatorExecutionInterceptor(registry);
  }

  private S3Client getS3Client() {
    if (s3Client != null) {
      return s3Client;
    }

    S3ClientBuilder builder = S3Client.builder();

    ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
    configureHttpClient(httpClientBuilder);
    builder.httpClientBuilder(httpClientBuilder);

    ClientOverrideConfiguration.Builder configBuilder = ClientOverrideConfiguration.builder();
    configureClientOverrides(configBuilder);
    configBuilder.addExecutionInterceptor(s3ArtifactRequestInterceptor);
    configBuilder.addExecutionInterceptor(spectatorExecutionInterceptor);
    builder.overrideConfiguration(configBuilder.build());

    if (!apiEndpoint.isEmpty()) {
      builder.endpointOverride(URI.create(apiEndpoint));
      builder.forcePathStyle(true);
      if (!apiRegion.isEmpty()) {
        builder.region(Region.of(apiRegion));
      }
    } else if (!region.isEmpty()) {
      builder.region(Region.of(region));
    }

    if (!awsAccessKeyId.isEmpty() && !awsSecretAccessKey.isEmpty()) {
      AwsBasicCredentials awsCreds = AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey);
      builder.credentialsProvider(StaticCredentialsProvider.create(awsCreds));
    }

    s3Client = builder.build();
    return s3Client;
  }

  @Override
  public InputStream download(Artifact artifact) throws IllegalArgumentException {
    String reference = artifact.getReference();
    if (reference.startsWith("s3://")) {
      reference = reference.substring("s3://".length());
    }

    int slash = reference.indexOf("/");
    if (slash <= 0) {
      throw new IllegalArgumentException(
          "S3 references must be of the format s3://<bucket>/<file-path>, got: " + artifact);
    }
    String bucketName = reference.substring(0, slash);
    String path = reference.substring(slash + 1);

    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(bucketName).key(path).build();

      InputStream objectContent = getS3Client().getObject(getObjectRequest);

      if (s3ArtifactValidator.isEmpty()) {
        return objectContent;
      }
      return s3ArtifactValidator.get().validate(getS3Client(), bucketName, path, objectContent);
    } catch (NoSuchKeyException e) {
      log.error("S3 object not found: s3://{}/{}: '{}'", bucketName, path, e.getMessage());
      // throw a more specific exception, to get more info to the caller
      // and such that the resulting http response code isn't 500 since
      // that this isn't a server error, nor is retryable.
      throw new NotFoundException("s3://" + bucketName + "/" + path + " not found", e);
    } catch (S3Exception e) {
      // An out-of-the-box S3Exception doesn't include the bucket/key
      // name so it's hard to know what's actually failing.
      log.error("exception getting object: s3://{}/{}: '{}'", bucketName, path, e.getMessage());
      throw e;
    }
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }

  private void configureHttpClient(ApacheHttpClient.Builder httpClientBuilder) {
    if (s3ArtifactProviderProperties.getConnectionMaxIdleMillis() != null) {
      httpClientBuilder.connectionMaxIdleTime(
          Duration.ofMillis(s3ArtifactProviderProperties.getConnectionMaxIdleMillis()));
    }
    if (s3ArtifactProviderProperties.getConnectionTimeout() != null) {
      httpClientBuilder.connectionTimeout(
          Duration.ofMillis(s3ArtifactProviderProperties.getConnectionTimeout()));
    }
    if (s3ArtifactProviderProperties.getConnectionTTL() != null) {
      httpClientBuilder.connectionTimeToLive(
          Duration.ofMillis(s3ArtifactProviderProperties.getConnectionTTL()));
    }
    if (s3ArtifactProviderProperties.getMaxConnections() != null) {
      httpClientBuilder.maxConnections(s3ArtifactProviderProperties.getMaxConnections());
    }
    if (s3ArtifactProviderProperties.getSocketTimeout() != null) {
      httpClientBuilder.socketTimeout(
          Duration.ofMillis(s3ArtifactProviderProperties.getSocketTimeout()));
    }
  }

  private void configureClientOverrides(ClientOverrideConfiguration.Builder configBuilder) {
    if (s3ArtifactProviderProperties.getClientExecutionTimeout() != null) {
      configBuilder.apiCallTimeout(
          Duration.ofMillis(s3ArtifactProviderProperties.getClientExecutionTimeout()));
    }
  }

  @NonnullByDefault
  static class S3ArtifactRequestInterceptor implements ExecutionInterceptor {

    /** The artifact account name */
    private final String name;

    private final S3ArtifactProviderProperties s3ArtifactProviderProperties;

    /** To prevent logging the same endpoint multiple times */
    private final Set<URI> endpoints = ConcurrentHashMap.newKeySet();

    S3ArtifactRequestInterceptor(
        String name, S3ArtifactProviderProperties s3ArtifactProviderProperties) {
      this.name = name;
      this.s3ArtifactProviderProperties = s3ArtifactProviderProperties;
    }

    @Override
    public void beforeTransmission(
        Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
      SdkHttpRequest request = context.httpRequest();

      if (s3ArtifactProviderProperties.isLogEndpoints() && endpoints.add(request.getUri())) {
        log.info(
            "S3ArtifactRequestInterceptor::beforeTransmission: name: {}, endpoint: '{}'",
            name,
            request.getUri().toString());
      }
    }
  }
}
