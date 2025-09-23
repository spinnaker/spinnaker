/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.front50.config;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Creates an S3 client.
 *
 * <p>Since there are multiple implementations of {@link S3Properties} and we create different
 * clients based on those properties, the actual factory code needed to be split out.
 */
public class S3ClientFactory {

  public static S3Client create(
      AwsCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    ApacheHttpClient.Builder httpClientBuilder =
        ApacheHttpClient.builder().connectionTimeout(Duration.ofSeconds(10));

    if (s3Properties.getProxyHost() != null && s3Properties.getProxyPort() != null) {
      String protocol = "http";
      if (s3Properties.getProxyProtocol() != null) {
        protocol = s3Properties.getProxyProtocol().toLowerCase();
      }
      String proxyUri =
          protocol + "://" + s3Properties.getProxyHost() + ":" + s3Properties.getProxyPort();

      ProxyConfiguration proxyConfig =
          ProxyConfiguration.builder().endpoint(URI.create(proxyUri)).build();

      httpClientBuilder.proxyConfiguration(proxyConfig);
    }

    S3Configuration.Builder s3ConfigBuilder = S3Configuration.builder();

    if (s3Properties.getPayloadSigning() != null) {
      s3ConfigBuilder.checksumValidationEnabled(s3Properties.getPayloadSigning());
    }

    if (s3Properties.getPathStyleAccess() != null) {
      s3ConfigBuilder.pathStyleAccessEnabled(s3Properties.getPathStyleAccess());
    }

    S3ClientBuilder s3Builder =
        S3Client.builder()
            .httpClientBuilder(httpClientBuilder)
            .credentialsProvider(awsCredentialsProvider)
            .serviceConfiguration(s3ConfigBuilder.build());

    if (!StringUtils.isEmpty(s3Properties.getEndpoint())) {
      s3Builder.endpointOverride(URI.create(s3Properties.getEndpoint()));

      if (!StringUtils.isEmpty(s3Properties.getRegionOverride())) {
        s3Builder.region(Region.of(s3Properties.getRegionOverride()));
      }
    } else {
      Optional.ofNullable(s3Properties.getRegion()).map(Region::of).ifPresent(s3Builder::region);
    }

    return s3Builder.build();
  }
}
