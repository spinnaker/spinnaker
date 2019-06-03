/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.subscription.AbstractAddSubscriptionCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Subscription;
import com.netflix.spinnaker.halyard.config.model.v1.pubsub.google.GoogleSubscription;

@Parameters(separators = "=")
public class GoogleAddSubscriptionCommand extends AbstractAddSubscriptionCommand {
  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION)
  private String jsonPath;

  @Parameter(
      names = "--template-path",
      converter = LocalFileConverter.class,
      description = GooglePubsubCommandProperties.TEMPLATE_PATH_DESCRIPTION)
  private String templatePath;

  @Parameter(names = "--project", description = GooglePubsubCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--subscription-name",
      description = GooglePubsubCommandProperties.SUBSCRIPTION_NAME_DESCRIPTION)
  private String subscriptionName;

  @Parameter(
      names = "--ack-deadline-seconds",
      description = GooglePubsubCommandProperties.ACK_DEADLINE_SECONDS_DESCRIPTION)
  private Integer ackDeadlineSeconds = 10;

  @Parameter(
      names = "--message-format",
      description = GooglePubsubCommandProperties.MESSAGE_FORMAT_DESCRIPTION)
  private GoogleSubscription.MessageFormat messageFormat = GoogleSubscription.MessageFormat.CUSTOM;

  @Override
  protected Subscription buildSubscription(String name) {
    return new GoogleSubscription()
        .setJsonPath(jsonPath)
        .setTemplatePath(templatePath)
        .setProject(project)
        .setSubscriptionName(subscriptionName)
        .setAckDeadlineSeconds(ackDeadlineSeconds)
        .setMessageFormat(messageFormat)
        .setName(name);
  }

  @Override
  protected Subscription emptySubscription() {
    return new GoogleSubscription();
  }

  @Override
  protected String getPubsubName() {
    return "google";
  }
}
