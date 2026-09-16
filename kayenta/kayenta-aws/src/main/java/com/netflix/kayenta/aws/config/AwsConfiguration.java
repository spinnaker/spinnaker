/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.aws.config;

import com.netflix.kayenta.aws.security.AwsCredentials;
import com.netflix.kayenta.aws.security.AwsNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@ConditionalOnProperty("kayenta.aws.enabled")
@ComponentScan({"com.netflix.kayenta.aws"})
@Slf4j
public class AwsConfiguration {

  @Bean
  @ConfigurationProperties("kayenta.aws")
  AwsConfigurationProperties awsConfigurationProperties() {
    return new AwsConfigurationProperties();
  }

  @Bean
  boolean registerAwsCredentials(
      AwsConfigurationProperties awsConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository)
      throws IOException {
    for (AwsManagedAccount awsManagedAccount : awsConfigurationProperties.getAccounts()) {
      String name = awsManagedAccount.getName();
      List<AccountCredentials.Type> supportedTypes = awsManagedAccount.getSupportedTypes();

      log.info("Registering AWS account {} with supported types {}.", name, supportedTypes);

      S3ClientBuilder s3ClientBuilder = S3Client.builder();

      String profileName = awsManagedAccount.getProfileName();
      if (!StringUtils.isEmpty(profileName)) {
        s3ClientBuilder.credentialsProvider(ProfileCredentialsProvider.create(profileName));
      }

      AwsManagedAccount.ExplicitAwsCredentials explicitCredentials =
          awsManagedAccount.getExplicitCredentials();
      if (explicitCredentials != null) {
        String sessionToken = explicitCredentials.getSessionToken();
        software.amazon.awssdk.auth.credentials.AwsCredentials sdkCreds =
            (sessionToken == null)
                ? AwsBasicCredentials.create(
                    explicitCredentials.getAccessKey(), explicitCredentials.getSecretKey())
                : AwsSessionCredentials.create(
                    explicitCredentials.getAccessKey(),
                    explicitCredentials.getSecretKey(),
                    sessionToken);
        s3ClientBuilder.credentialsProvider(StaticCredentialsProvider.create(sdkCreds));
      }

      String proxyProtocol = awsManagedAccount.getProxyProtocol();
      if (proxyProtocol != null) {
        String proxyHost = awsManagedAccount.getProxyHost();
        String proxyPort = awsManagedAccount.getProxyPort();
        if (proxyHost != null && proxyPort != null) {
          String proxyUri = proxyProtocol.toLowerCase() + "://" + proxyHost + ":" + proxyPort;
          s3ClientBuilder.httpClientBuilder(
              ApacheHttpClient.builder()
                  .proxyConfiguration(
                      ProxyConfiguration.builder().endpoint(URI.create(proxyUri)).build()));
        }
      }

      String endpoint = awsManagedAccount.getEndpoint();
      if (!StringUtils.isEmpty(endpoint)) {
        s3ClientBuilder.endpointOverride(URI.create(endpoint));
        s3ClientBuilder.serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(true).build());
        String region = Optional.ofNullable(awsManagedAccount.getRegion()).orElse("us-east-1");
        s3ClientBuilder.region(Region.of(region));
      } else {
        Optional.ofNullable(awsManagedAccount.getRegion())
            .map(Region::of)
            .ifPresent(s3ClientBuilder::region);
      }

      S3Client s3Client = s3ClientBuilder.build();

      try {
        AwsCredentials awsCredentials = new AwsCredentials();
        AwsNamedAccountCredentials.AwsNamedAccountCredentialsBuilder
            awsNamedAccountCredentialsBuilder =
                AwsNamedAccountCredentials.builder().name(name).credentials(awsCredentials);

        if (!CollectionUtils.isEmpty(supportedTypes)) {
          if (supportedTypes.contains(AccountCredentials.Type.OBJECT_STORE)) {
            String bucket = awsManagedAccount.getBucket();
            String rootFolder = awsManagedAccount.getRootFolder();

            if (StringUtils.isEmpty(bucket)) {
              throw new IllegalArgumentException(
                  "AWS/S3 account " + name + " is required to specify a bucket.");
            }

            if (StringUtils.isEmpty(rootFolder)) {
              throw new IllegalArgumentException(
                  "AWS/S3 account " + name + " is required to specify a rootFolder.");
            }

            awsNamedAccountCredentialsBuilder.bucket(bucket);
            awsNamedAccountCredentialsBuilder.region(awsManagedAccount.getRegion());
            awsNamedAccountCredentialsBuilder.rootFolder(rootFolder);
            awsNamedAccountCredentialsBuilder.s3Client(s3Client);
          }

          awsNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
        }

        AwsNamedAccountCredentials awsNamedAccountCredentials =
            awsNamedAccountCredentialsBuilder.build();
        accountCredentialsRepository.save(name, awsNamedAccountCredentials);
      } catch (Throwable t) {
        log.error("Could not load AWS account " + name + ".", t);
      }
    }

    return true;
  }
}
