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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.netflix.kayenta.aws.security.AwsCredentials;
import com.netflix.kayenta.aws.security.AwsNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties
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
  boolean registerAwsCredentials(AwsConfigurationProperties awsConfigurationProperties,
                                 AWSCredentialsProvider awsCredentialsProvider,
                                 AccountCredentialsRepository accountCredentialsRepository) throws IOException {
    for (AwsManagedAccount awsManagedAccount : awsConfigurationProperties.getAccounts()) {
      String name = awsManagedAccount.getName();
      List<AccountCredentials.Type> supportedTypes = awsManagedAccount.getSupportedTypes();

      log.info("Registering AWS account {} with supported types {}.", name, supportedTypes);

      ClientConfiguration clientConfiguration = new ClientConfiguration();

      if (awsManagedAccount.getProxyProtocol() != null) {
        if (awsManagedAccount.getProxyProtocol().equalsIgnoreCase("HTTPS")) {
          clientConfiguration.setProtocol(Protocol.HTTPS);
        } else {
          clientConfiguration.setProtocol(Protocol.HTTP);
        }
        Optional.ofNullable(awsManagedAccount.getProxyHost())
          .ifPresent(clientConfiguration::setProxyHost);
        Optional.ofNullable(awsManagedAccount.getProxyPort())
          .map(Integer::parseInt)
          .ifPresent(clientConfiguration::setProxyPort);
      }

      AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, clientConfiguration);

      if (awsManagedAccount.getEndpoint() != null) {
        client.setEndpoint(awsManagedAccount.getEndpoint());
        client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
      } else {
        Optional.ofNullable(awsManagedAccount.getRegion())
          .map(Regions::fromName)
          .map(Region::getRegion)
          .ifPresent(client::setRegion);
      }

      try {
        AwsCredentials awsCredentials = new AwsCredentials();
        AwsNamedAccountCredentials.AwsNamedAccountCredentialsBuilder awsNamedAccountCredentialsBuilder =
          AwsNamedAccountCredentials
            .builder()
            .name(name)
            .credentials(awsCredentials);

        if (!CollectionUtils.isEmpty(supportedTypes)) {
          if (supportedTypes.contains(AccountCredentials.Type.OBJECT_STORE)) {
            String bucket = awsManagedAccount.getBucket();
            String rootFolder = awsManagedAccount.getRootFolder();

            if (StringUtils.isEmpty(bucket)) {
              throw new IllegalArgumentException("AWS/S3 account " + name + " is required to specify a bucket.");
            }

            if (StringUtils.isEmpty(rootFolder)) {
              throw new IllegalArgumentException("AWS/S3 account " + name + " is required to specify a rootFolder.");
            }

            awsNamedAccountCredentialsBuilder.bucket(bucket);
            awsNamedAccountCredentialsBuilder.region(awsManagedAccount.getRegion());
            awsNamedAccountCredentialsBuilder.rootFolder(rootFolder);
            awsNamedAccountCredentialsBuilder.amazonS3(client);
          }

          awsNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
        }

        AwsNamedAccountCredentials awsNamedAccountCredentials = awsNamedAccountCredentialsBuilder.build();
        accountCredentialsRepository.save(name, awsNamedAccountCredentials);
      } catch (Throwable t) {
        log.error("Could not load AWS account " + name + ".", t);
      }
    }

    return true;
  }
}
