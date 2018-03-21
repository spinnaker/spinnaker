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

package com.netflix.spinnaker.echo.pubsub.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.config.google.GooglePubsubProperties;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.echo.pubsub.utils.NodeIdentity;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.threeten.bp.Duration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class GooglePubsubSubscriber implements PubsubSubscriber {

  private String subscriptionName;

  private String name;

  @Getter
  private Subscriber subscriber;

  static private final PubsubSystem pubsubSystem = PubsubSystem.GOOGLE;


  public GooglePubsubSubscriber(String name, String subscriptionName, String project, Subscriber subscriber) {
    this.name = name;
    this.subscriptionName = formatSubscriptionName(project, subscriptionName);
    this.subscriber = subscriber;
  }

  @Override
  public PubsubSystem pubsubSystem() {
    return pubsubSystem;
  }

  @Override
  public String subscriptionName() {
    return subscriptionName;
  }

  @Override
  public String getName() {
    return name;
  }

  private static String formatSubscriptionName(String project, String name) {
    return String.format("projects/%s/subscriptions/%s", project, name);
  }

  public static GooglePubsubSubscriber buildSubscriber(GooglePubsubProperties.GooglePubsubSubscription subscription,
                                                       PubsubMessageHandler pubsubMessageHandler) {
    Subscriber subscriber;
    String subscriptionName = subscription.getSubscriptionName();
    String project = subscription.getProject();
    String jsonPath = subscription.getJsonPath();

    GooglePubsubMessageReceiver messageReceiver = new GooglePubsubMessageReceiver(subscription.getAckDeadlineSeconds(),
        subscription.getName(),
        pubsubMessageHandler,
        subscription.readTemplatePath());

    Credentials credentials = null;
    if (jsonPath != null && !jsonPath.isEmpty()) {
      try {
        credentials = ServiceAccountCredentials.fromStream(new FileInputStream(jsonPath));
      } catch (IOException e) {
        log.error("Could not import Google Pubsub json credentials: {}", e.getMessage());
      }
    } else {
      try {
        credentials = GoogleCredentials.getApplicationDefault();
      } catch (IOException e) {
        log.error("Could not import default application credentials: {}", e.getMessage());
      }
    }

    subscriber = Subscriber
        .defaultBuilder(SubscriptionName.create(project, subscriptionName), messageReceiver)
        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
        .setMaxAckExtensionPeriod(Duration.ofSeconds(0))
        .build();

    subscriber.addListener(new GooglePubsubFailureHandler(formatSubscriptionName(project, subscriptionName)), MoreExecutors.directExecutor());

    return new GooglePubsubSubscriber(subscription.getName(), subscriptionName, project, subscriber);
  }


  private static class GooglePubsubMessageReceiver implements MessageReceiver {

    private Integer ackDeadlineSeconds;

    private PubsubMessageHandler pubsubMessageHandler;

    /**
     * Logical name given to the subscription by the user, not the locator
     * the pub/sub system uses.
     */
    private String subscriptionName;

    private NodeIdentity identity = new NodeIdentity();

    private MessageArtifactTranslator messageArtifactTranslator;

    private ObjectMapper objectMapper = new ObjectMapper();

    public GooglePubsubMessageReceiver(Integer ackDeadlineSeconds,
                                       String subscriptionName,
                                       PubsubMessageHandler pubsubMessageHandler,
                                       InputStream templateStream) {
      this.ackDeadlineSeconds = ackDeadlineSeconds;
      this.subscriptionName = subscriptionName;
      this.pubsubMessageHandler = pubsubMessageHandler;
      this.messageArtifactTranslator = new MessageArtifactTranslator(templateStream);
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      String messagePayload = message.getData().toStringUtf8();
      String messageId = message.getMessageId();
      Map messageAttributes = message.getAttributesMap() == null ? new HashMap<>() : message.getAttributesMap();
      log.debug("Received Google pub/sub message with payload: {}\n and attributes: {}", messagePayload, messageAttributes);
      MessageDescription description = MessageDescription.builder()
        .subscriptionName(subscriptionName)
        .messagePayload(messagePayload)
        .messageAttributes(messageAttributes)
        .pubsubSystem(pubsubSystem)
        .ackDeadlineMillis(5 * TimeUnit.SECONDS.toMillis(ackDeadlineSeconds)) // Set a high upper bound on message processing time.
        .retentionDeadlineMillis(TimeUnit.DAYS.toMillis(7)) // Expire key after max retention time, which is 7 days.
        .build();
      GoogleMessageAcknowledger acknowledger = new GoogleMessageAcknowledger(consumer);

      try {
        List<Artifact> artifacts = messageArtifactTranslator.parseArtifacts(messagePayload);
        description.setArtifacts(artifacts);
        log.info("artifacts {}", String.join(", ", artifacts.stream().map(Artifact::toString).collect(Collectors.toList())));
      } catch (Exception e) {
        log.error("Failed to process artifacts: {}", e.getMessage(), e);
        pubsubMessageHandler.handleFailedMessage(description, acknowledger, identity.getIdentity(), messageId);
        return;
      }

      pubsubMessageHandler.handleMessage(description, acknowledger, identity.getIdentity(), messageId);
    }
  }

  @AllArgsConstructor
  private static class GooglePubsubFailureHandler extends ApiService.Listener {

    private String subscriptionName;

    @Override
    public void failed(ApiService.State from, Throwable failure) {
      log.error("Google Pubsub listener for subscription name {} failure caused by {}", subscriptionName, failure.getMessage());
    }
  }
}
