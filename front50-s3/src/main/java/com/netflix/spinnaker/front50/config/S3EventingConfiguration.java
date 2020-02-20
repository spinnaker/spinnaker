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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.EventingS3ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.TemporarySQSQueue;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"spinnaker.s3.enabled", "spinnaker.s3.eventing.enabled"})
public class S3EventingConfiguration {

  @Bean
  public AmazonSQS awsSQSClient(
      AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(awsCredentialsProvider)
        .withClientConfiguration(new ClientConfiguration())
        .withRegion(s3Properties.getRegion())
        .build();
  }

  @Bean
  public AmazonSNS awsSNSClient(
      AWSCredentialsProvider awsCredentialsProvider, S3Properties s3Properties) {
    return AmazonSNSClientBuilder.standard()
        .withCredentials(awsCredentialsProvider)
        .withClientConfiguration(new ClientConfiguration())
        .withRegion(s3Properties.getRegion())
        .build();
  }

  @Bean
  public TemporarySQSQueue temporaryQueueSupport(
      Optional<ApplicationInfoManager> applicationInfoManager,
      AmazonSQS amazonSQS,
      AmazonSNS amazonSNS,
      S3Properties s3Properties) {
    return new TemporarySQSQueue(
        amazonSQS,
        amazonSNS,
        s3Properties.eventing.getSnsTopicName(),
        getInstanceId(applicationInfoManager));
  }

  @Bean
  public ObjectKeyLoader eventingS3ObjectKeyLoader(
      ObjectMapper objectMapper,
      S3Properties s3Properties,
      StorageService storageService,
      TemporarySQSQueue temporaryQueueSupport,
      Registry registry) {
    return new EventingS3ObjectKeyLoader(
        Executors.newFixedThreadPool(1),
        objectMapper,
        s3Properties,
        temporaryQueueSupport,
        storageService,
        registry,
        true);
  }

  /**
   * This will likely need improvement should it ever need to run in a non-eureka environment.
   *
   * @return instance identifier that will be used to create a uniquely named sqs queue
   */
  private static String getInstanceId(Optional<ApplicationInfoManager> applicationInfoManager) {
    if (applicationInfoManager.isPresent()) {
      return applicationInfoManager.get().getInfo().getInstanceId();
    }

    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }
}
