/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.notifications

import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import org.springframework.beans.factory.annotation.Autowired

/**
 *
 */
class SuspendedPipelinesPollingNotificationAgent extends AbstractPollingNotificationAgent {

  static final String NOTIFICATION_TYPE = "suspendedPipeline"
  long pollingInterval = 60
  String notificationType = ""

  @Autowired
  PipelineConfigurationService pipelineConfigurationService

  @Autowired
  SuspendedPipelinesPollingNotificationAgent(List<NotificationHandler> notificationHandlers) {
    super(notificationHandlers)
  }

  @Override
  void handleNotification(List<Map> pipelines) {
    Date now = new Date()
    for (Map pipeline in pipelines) {
      if (pipeline.scheduledDate != null && now.compareTo(pipeline.scheduledDate as Date) in [0,1]) {
        notify(pipeline)
      }
    }
  }

  @Override
  void run() {
    handleNotification(pipelineConfigurationService.pipelines)
  }
}
