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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notification;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notifications;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.GithubStatusNotification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.SlackNotification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.TwilioNotification;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfigs notifications.
 */
@Component
public class NotificationService {
  @Autowired private LookupService lookupService;

  @Autowired private ValidateService validateService;

  @Autowired private DeploymentService deploymentService;

  public Notification getNotification(String deploymentName, String notificationName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setNotification(notificationName);

    List<Notification> matching = lookupService.getMatchingNodesOfType(filter, Notification.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "No notification type with name \"" + notificationName + "\" could be found")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one notification type with name \"" + notificationName + "\" found")
                .build());
    }
  }

  public Notifications getNotifications(String deploymentName) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Notifications notifications = deploymentConfiguration.getNotifications();
    if (notifications == null) {
      notifications = new Notifications();
      deploymentConfiguration.setNotifications(notifications);
    }

    return notifications;
  }

  public void setNotification(String deploymentName, Notification notification) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Notifications notifications = deploymentConfiguration.getNotifications();
    switch (notification.getNotificationType()) {
      case SLACK:
        notifications.setSlack((SlackNotification) notification);
        break;
      case TWILIO:
        notifications.setTwilio((TwilioNotification) notification);
        break;
      case GITHUB_STATUS:
        notifications.setGithubStatus((GithubStatusNotification) notification);
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown notification type " + notification.getNotificationType());
    }
  }

  public void setEnabled(String deploymentName, String notificationName, boolean enabled) {
    Notification notification = getNotification(deploymentName, notificationName);
    notification.setEnabled(enabled);
  }

  public ProblemSet validateNotification(String deploymentName, String notificationName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setNotification(notificationName);

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllNotifications(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyNotification();

    return validateService.validateMatchingFilter(filter);
  }
}
