/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.pubsub.GoogleCloudBuildEventCreator;
import com.netflix.spinnaker.echo.pubsub.PubsubEventCreator;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.echo.pubsub.google.GooglePubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ConditionalOnProperty("gcb.enabled")
@EnableConfigurationProperties(GoogleCloudBuildProperties.class)
@RequiredArgsConstructor
public class GoogleCloudBuildConfig {
  private final PubsubSubscribers pubsubSubscribers;
  private final PubsubMessageHandler.Factory pubsubMessageHandlerFactory;
  private final GoogleCloudBuildEventCreator googleCloudBuildEventCreator;
  @Valid private final GoogleCloudBuildProperties googleCloudBuildProperties;
  private final MessageArtifactTranslator.Factory messageArtifactTranslatorFactory;

  @PostConstruct
  void googleCloudBuildSubscribers() {
    log.info("Creating Google Pubsub Subscribers");
    List<PubsubSubscriber> newSubscribers = new ArrayList<>();

    googleCloudBuildProperties
        .getAccounts()
        .forEach(
            (GoogleCloudBuildProperties.Account account) -> {
              GooglePubsubProperties.GooglePubsubSubscription subscription =
                  GooglePubsubProperties.GooglePubsubSubscription.builder()
                      .name(account.getName())
                      .project(account.getProject())
                      .subscriptionName(account.getSubscriptionName())
                      .jsonPath(account.getJsonKey())
                      .messageFormat(GooglePubsubProperties.MessageFormat.GCB)
                      .build();
              log.info(
                  "Bootstrapping Google Cloud Build Pubsub Subscriber listening to subscription: {} in project: {}",
                  account.getSubscriptionName(),
                  account.getProject());

              Optional<MessageArtifactTranslator> messageArtifactTranslator =
                  Optional.ofNullable(subscription.readTemplatePath())
                      .map(messageArtifactTranslatorFactory::createJinja);
              PubsubEventCreator pubsubEventCreator =
                  new PubsubEventCreator(messageArtifactTranslator);

              PubsubMessageHandler pubsubMessageHandler =
                  pubsubMessageHandlerFactory.create(
                      Arrays.asList(pubsubEventCreator, googleCloudBuildEventCreator));

              GooglePubsubSubscriber subscriber =
                  GooglePubsubSubscriber.buildSubscriber(subscription, pubsubMessageHandler);

              newSubscribers.add(subscriber);
            });
    pubsubSubscribers.putAll(newSubscribers);
  }
}
