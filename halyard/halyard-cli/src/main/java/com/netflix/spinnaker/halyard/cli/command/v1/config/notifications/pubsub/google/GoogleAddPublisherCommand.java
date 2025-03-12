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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.pubsub.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.pubsub.AbstractAddPublisherCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.google.GooglePubsubCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Publisher;
import com.netflix.spinnaker.halyard.config.model.v1.pubsub.google.GooglePublisher;
import lombok.Getter;

@Parameters(separators = "=")
public class GoogleAddPublisherCommand extends AbstractAddPublisherCommand {

  @Getter private String pubsubName = "google";

  @Parameter(names = "--project", description = GooglePubsubCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION)
  private String jsonPath;

  @Parameter(
      names = "--topic-name",
      description = GooglePubsubCommandProperties.TOPIC_NAME_DESCRIPTION)
  private String topicName;

  @Override
  protected Publisher buildPublisher(String publisherName) {
    return new GooglePublisher()
        .setProject(project)
        .setTopicName(topicName)
        .setJsonPath(jsonPath)
        .setContent(GooglePublisher.Content.NOTIFICATIONS)
        .setName(publisherName);
  }
}
