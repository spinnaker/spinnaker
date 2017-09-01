/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config.google;

import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.echo.pubsub.google.GooglePubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
@ConditionalOnProperty("pubsub.google.enabled")
@EnableConfigurationProperties(GooglePubsubProperties.class)
public class GooglePubsubConfig {

  @Autowired
  private PubsubSubscribers pubsubSubscribers;

  @Autowired
  private PubsubMessageHandler pubsubMessageHandler;

  @Valid
  @Autowired
  private GooglePubsubProperties googlePubsubProperties;

  @PostConstruct
  void googlePubsubSubscribers() {
    log.info("Creating Google Pubsub Subscribers");
    List<PubsubSubscriber> newSubscribers = new ArrayList<>();
    googlePubsubProperties.getSubscriptions().forEach((GooglePubsubProperties.GooglePubsubSubscription subscription) -> {
      String name = subscription.getName();
      String project = subscription.getProject();
      String jsonPath = subscription.getJsonPath();
      String templatePath = subscription.getTemplatePath();

      log.info("Bootstrapping Google Pubsub Subscriber listening to subscription: {} in project: {}",
          subscription.getName(),
          subscription.getProject());
      GooglePubsubSubscriber subscriber = GooglePubsubSubscriber
          .buildSubscriber(name, project, jsonPath, subscription.getAckDeadlineSeconds(), pubsubMessageHandler, templatePath);

      newSubscribers.add(subscriber);
    });
    pubsubSubscribers.putAll(newSubscribers);
  }
}
