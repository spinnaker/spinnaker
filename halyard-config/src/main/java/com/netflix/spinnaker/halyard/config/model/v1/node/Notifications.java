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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.GithubStatusNotification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.SlackNotification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.TwilioNotification;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Notifications extends Node implements Cloneable {
  SlackNotification slack = new SlackNotification();
  TwilioNotification twilio = new TwilioNotification();

  @JsonProperty("github-status")
  GithubStatusNotification githubStatus = new GithubStatusNotification();

  @Override
  public String getNodeName() {
    return "notification";
  }

  @JsonIgnore
  public boolean isEnabled() {
    return slack.isEnabled() || twilio.isEnabled() || githubStatus.isEnabled();
  }

  public static Class<? extends Notification> translateNotificationType(String notificationName) {
    Class<? extends Notification> n;
    switch (Notification.NotificationType.valueOf(notificationName)) {
      case SLACK:
        return SlackNotification.class;
      case TWILIO:
        return TwilioNotification.class;
      case GITHUB_STATUS:
        return GithubStatusNotification.class;
      default:
        throw new IllegalArgumentException(
            "No notification type with name \"" + notificationName + "\" handled by halyard");
    }
  }
}
