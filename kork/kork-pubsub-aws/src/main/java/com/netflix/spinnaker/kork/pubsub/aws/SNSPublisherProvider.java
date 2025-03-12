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

package com.netflix.spinnaker.kork.pubsub.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.aws.ARN;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.aws.config.AmazonPubsubProperties;
import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates one SNSPublisher per subscription */
@Component
@ConditionalOnProperty({"pubsub.enabled", "pubsub.amazon.enabled"})
public class SNSPublisherProvider {
  private static final Logger log = LoggerFactory.getLogger(SNSPublisherProvider.class);

  private final AWSCredentialsProvider awsCredentialsProvider;
  private final AmazonPubsubProperties properties;
  private final PubsubPublishers pubsubPublishers;
  private final Registry registry;
  private final RetrySupport retrySupport;
  private final DiscoveryStatusListener discoveryStatus;
  private final DynamicConfigService dynamicConfig;

  @Autowired
  public SNSPublisherProvider(
      AWSCredentialsProvider awsCredentialsProvider,
      AmazonPubsubProperties properties,
      PubsubPublishers pubsubPublishers,
      Registry registry,
      RetrySupport retrySupport,
      DiscoveryStatusListener discoveryStatus,
      DynamicConfigService dynamicConfig) {
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.properties = properties;
    this.pubsubPublishers = pubsubPublishers;
    this.registry = registry;
    this.retrySupport = retrySupport;
    this.discoveryStatus = discoveryStatus;
    this.dynamicConfig = dynamicConfig;
  }

  @PostConstruct
  public void start() {
    if (properties == null) {
      return;
    }

    List<PubsubPublisher> publishers = new ArrayList<>();
    properties
        .getSubscriptions()
        .forEach(
            (AmazonPubsubProperties.AmazonPubsubSubscription subscription) -> {
              ARN topicARN = new ARN(subscription.getTopicARN());
              log.info("Bootstrapping SNS topic: {}", topicARN);

              AmazonSNS amazonSNS =
                  AmazonSNSClientBuilder.standard()
                      .withCredentials(awsCredentialsProvider)
                      .withClientConfiguration(new ClientConfiguration())
                      .withRegion(topicARN.getRegion())
                      .build();

              Supplier<Boolean> isEnabled =
                  PubSubUtils.getEnabledSupplier(dynamicConfig, subscription, discoveryStatus);

              SNSPublisher publisher =
                  new SNSPublisher(subscription, amazonSNS, isEnabled, registry, retrySupport);

              publishers.add(publisher);
            });

    pubsubPublishers.putAll(publishers);
  }
}
