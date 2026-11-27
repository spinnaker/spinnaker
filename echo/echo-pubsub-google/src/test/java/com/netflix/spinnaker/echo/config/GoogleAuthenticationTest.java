/*
 * Copyright 2026 spinnaker.io
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
package com.netflix.spinnaker.echo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.pubsub.GoogleCloudBuildEventCreator;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.echo.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.echo.pubsub.google.GoogleCloudBuildArtifactExtractor;
import com.netflix.spinnaker.echo.pubsub.google.GooglePubsubPublisher;
import com.netflix.spinnaker.echo.pubsub.google.GooglePubsubSubscriber;
import com.netflix.spinnaker.kork.google.auth.GoogleAuthProperties;
import com.netflix.spinnaker.kork.google.auth.GoogleCredentialsFactory;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GoogleAuthenticationTest {
  @Test
  void providerPassesConsumerScopesToTheSharedFactory() throws IOException {
    GoogleAuthProperties auth = new GoogleAuthProperties();
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    GoogleCredentialsFactory shared = mock(GoogleCredentialsFactory.class);
    when(shared.create(auth, SubscriberStubSettings.getDefaultServiceScopes()))
        .thenReturn(credentials);

    assertThat(new GooglePubsubCredentialsProvider(auth, shared).getCredentials())
        .isSameAs(credentials);
    verify(shared).create(auth, SubscriberStubSettings.getDefaultServiceScopes());
  }

  @Test
  void subscriberDoesNotContinueWithNullCredentialsAfterFailure() {
    GooglePubsubProperties properties =
        pubsub(
            Map.of(
                "pubsub.google.subscriptions[0].name", "events",
                "pubsub.google.subscriptions[0].project", "test-project",
                "pubsub.google.subscriptions[0].subscription-name", "events",
                "pubsub.google.subscriptions[0].auth.credentials-file",
                    "/nonexistent/google-auth-test.json"));

    assertThatThrownBy(
            () ->
                GooglePubsubSubscriber.buildSubscriber(
                    properties.getSubscriptions().get(0), mock(PubsubMessageHandler.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void publisherDoesNotContinueWithANullClientAfterCredentialFailure() {
    GooglePubsubProperties properties =
        pubsub(
            Map.of(
                "pubsub.google.publishers[0].name", "events",
                "pubsub.google.publishers[0].project", "test-project",
                "pubsub.google.publishers[0].topic-name", "events",
                "pubsub.google.publishers[0].auth.credentials-file",
                    "/nonexistent/google-auth-test.json"));

    assertThatThrownBy(
            () ->
                GooglePubsubPublisher.buildPublisher(
                    properties.getPublishers().get(0), mock(ObjectMapper.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"subscriptions", "publishers"})
  void rejectsRemovedPubsubJsonPath(String collection) {
    assertThatThrownBy(
            () -> pubsub(Map.of("pubsub.google." + collection + "[0].json-path", "unused")))
        .hasRootCauseMessage("jsonPath is no longer supported; use auth.credentialsFile");
  }

  @Test
  void publisherBindsTheStandardAuthBlock() {
    GooglePubsubProperties properties =
        pubsub(
            Map.of(
                "pubsub.google.publishers[0].auth.credentials-file", "credentials.json",
                "pubsub.google.publishers[0].auth.impersonate-service-account",
                    "publisher@identity-project.iam.gserviceaccount.com"));
    GoogleAuthProperties auth = properties.getPublishers().get(0).getAuth();
    assertThat(auth.getCredentialsFile()).isEqualTo("credentials.json");
    assertThat(auth.getImpersonateServiceAccount())
        .isEqualTo("publisher@identity-project.iam.gserviceaccount.com");
  }

  @Test
  void cloudBuildPassesAuthToTheGeneratedSubscriber() {
    GoogleCloudBuildProperties properties =
        gcb(
            Map.of(
                "gcb.accounts[0].name",
                "builds",
                "gcb.accounts[0].project",
                "test-project",
                "gcb.accounts[0].subscription-name",
                "build-events",
                "gcb.accounts[0].auth.impersonate-service-account",
                "subscriber@identity-project.iam.gserviceaccount.com"));
    GoogleCloudBuildProperties.Account account = properties.getAccounts().get(0);
    PubsubSubscribers subscribers = mock(PubsubSubscribers.class);
    MessageArtifactTranslator.Factory translatorFactory =
        mock(MessageArtifactTranslator.Factory.class);
    when(translatorFactory.create(any())).thenReturn(mock(MessageArtifactTranslator.class));
    GoogleCloudBuildConfig config =
        new GoogleCloudBuildConfig(
            subscribers,
            mock(PubsubMessageHandler.Factory.class),
            mock(GoogleCloudBuildEventCreator.class),
            properties,
            translatorFactory,
            mock(GoogleCloudBuildArtifactExtractor.Factory.class));

    try (MockedStatic<GooglePubsubSubscriber> subscriber =
        mockStatic(GooglePubsubSubscriber.class)) {
      subscriber
          .when(() -> GooglePubsubSubscriber.buildSubscriber(any(), any()))
          .thenAnswer(
              invocation -> {
                GooglePubsubProperties.GooglePubsubSubscription subscription =
                    invocation.getArgument(0);
                assertThat(subscription.getAuth()).isSameAs(account.getAuth());
                assertThat(subscription.getSubscriptionName()).isEqualTo("build-events");
                return mock(GooglePubsubSubscriber.class);
              });
      config.googleCloudBuildSubscribers();
      subscriber.verify(() -> GooglePubsubSubscriber.buildSubscriber(any(), any()));
      verify(subscribers).putAll(anyList());
    }
  }

  @Test
  void rejectsRemovedCloudBuildJsonKey() {
    assertThatThrownBy(() -> gcb(Map.of("gcb.accounts[0].json-key", "unused")))
        .hasRootCauseMessage("jsonKey is no longer supported; use auth.credentialsFile");
  }

  private static GooglePubsubProperties pubsub(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("pubsub.google", GooglePubsubProperties.class)
        .get();
  }

  private static GoogleCloudBuildProperties gcb(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bind("gcb", GoogleCloudBuildProperties.class)
        .get();
  }
}
