/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.echo.pubsub.aws;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.config.AmazonPubsubConfig;
import com.netflix.spinnaker.echo.config.PubsubConfig;
import com.netflix.spinnaker.echo.events.EventPropagator;
import com.netflix.spinnaker.echo.pubsub.PubsubMessageHandler;
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor;
import com.netflix.spinnaker.kork.aws.AwsComponents;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class SQSSubscriberProviderBeanTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(NoopRegistry.class)
          .withBean(ObjectMapper.class)
          .withBean(EventPropagator.class, () -> mock(EventPropagator.class))
          .withBean(JinjaArtifactExtractor.Factory.class)
          .withBean(DefaultJinjavaFactory.class)
          .withBean(DynamicConfigService.NoopDynamicConfig.class)
          .withBean(DiscoveryStatusListener.class)
          .withUserConfiguration(
              AwsComponents.class,
              SQSSubscriberProvider.class,
              AmazonPubsubConfig.class,
              PubsubConfig.class,
              PubsubMessageHandler.Factory.class,
              MessageArtifactTranslator.Factory.class)
          .withPropertyValues(
              "pubsub.enabled=true",
              "pubsub.amazon.enabled=true",
              "pubsub.amazon.subscriptions[0].name=test-subscription",
              "pubsub.amazon.subscriptions[0].queueARN=arn:aws:sqs:us-east-1:123456789012:test-queue",
              "pubsub.amazon.subscriptions[0].topicARN=arn:aws:sns:us-east-1:123456789012:test-topic");

  @Test
  void awsCredentialsProviderBeanIsAvailable() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SQSSubscriberProvider.class);
          assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
        });
  }
}
