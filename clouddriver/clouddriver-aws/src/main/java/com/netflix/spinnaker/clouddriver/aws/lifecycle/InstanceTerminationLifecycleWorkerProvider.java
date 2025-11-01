/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("amazonCredentialsLoader")
@ConditionalOnExpression(
    "${aws.lifecycle-subscribers.instance-termination.enabled:false} && ${caching.write-enabled:true}")
public class InstanceTerminationLifecycleWorkerProvider {
  private static final String REGION_TEMPLATE_PATTERN = Pattern.quote("{{region}}");
  private static final String ACCOUNT_ID_TEMPLATE_PATTERN = Pattern.quote("{{accountId}}");

  private static final Logger log =
      LoggerFactory.getLogger(InstanceTerminationLifecycleWorkerProvider.class);

  private final ObjectMapper objectMapper;
  private final AmazonClientProvider amazonClientProvider;
  private final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;
  private final InstanceTerminationConfigurationProperties properties;
  private final Provider<AwsEurekaSupport> discoverySupport;
  private final Registry registry;

  @Autowired
  InstanceTerminationLifecycleWorkerProvider(
      @Qualifier("amazonObjectMapper") ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      InstanceTerminationConfigurationProperties properties,
      Provider<AwsEurekaSupport> discoverySupport,
      Registry registry) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.credentialsRepository = credentialsRepository;
    this.properties = properties;
    this.discoverySupport = discoverySupport;
    this.registry = registry;
  }

  @PostConstruct
  public void start() {
    NetflixAmazonCredentials credentials =
        credentialsRepository.getOne(properties.getAccountName());
    ExecutorService executorService =
        Executors.newFixedThreadPool(
            credentials.getRegions().size(),
            new ThreadFactoryBuilder()
                .setNameFormat(
                    InstanceTerminationLifecycleWorkerProvider.class.getSimpleName() + "-%d")
                .build());

    credentials
        .getRegions()
        .forEach(
            region -> {
              InstanceTerminationLifecycleWorker worker =
                  new InstanceTerminationLifecycleWorker(
                      objectMapper,
                      amazonClientProvider,
                      credentialsRepository,
                      new InstanceTerminationConfigurationProperties(
                          properties.getAccountName(),
                          properties
                              .getQueueARN()
                              .replaceAll(REGION_TEMPLATE_PATTERN, region.getName())
                              .replaceAll(ACCOUNT_ID_TEMPLATE_PATTERN, credentials.getAccountId()),
                          properties
                              .getTopicARN()
                              .replaceAll(REGION_TEMPLATE_PATTERN, region.getName())
                              .replaceAll(ACCOUNT_ID_TEMPLATE_PATTERN, credentials.getAccountId()),
                          properties.getVisibilityTimeout(),
                          properties.getWaitTimeSeconds(),
                          properties.getSqsMessageRetentionPeriodSeconds(),
                          properties.getEurekaUpdateStatusRetryMax()),
                      discoverySupport,
                      registry);
              try {
                executorService.submit(worker);
              } catch (RejectedExecutionException e) {
                log.error("Could not start " + worker.getWorkerName(), e);
              }
            });
  }
}
