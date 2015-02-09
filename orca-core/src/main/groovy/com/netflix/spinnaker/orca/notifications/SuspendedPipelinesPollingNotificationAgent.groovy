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
import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
/**
 *
 */
@Component
class SuspendedPipelinesPollingNotificationAgent extends AbstractPollingNotificationAgent {

  static final String NOTIFICATION_TYPE = "suspendedPipeline"
  long pollingInterval = 120
  String notificationType = NOTIFICATION_TYPE

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  SuspendedPipelinesPollingNotificationAgent(List<NotificationHandler> notificationHandlers) {
    super(notificationHandlers)
  }

  @Override
  void handleNotification(List<Map> pipelines) {
    long now = new Date().time
    for (Map pipeline in pipelines.findAll { it.status == ExecutionStatus.SUSPENDED.name() }) {
      def stages = pipeline.stages as List<Map>
      def scheduledStage = stages.find { now >= extractScheduledTime(it) }
      if (scheduledStage != null) {
        notify(pipeline)
      }
    }
  }

  @Override
  void run() {
    try {
      List<Pipeline> pipelines = executionRepository.retrievePipelines()
      List<Map> pipelineMaps = objectMapper.convertValue(pipelines, new TypeReference<List<Map>>() { })
      handleNotification(pipelineMaps)
    } catch (Exception e) {
      e.printStackTrace()
    }
  }

  private long extractScheduledTime(Map stage) {
    long scheduledTime = Long.MAX_VALUE
    try {
      scheduledTime = stage.scheduledTime != null && stage.scheduledTime as long != 0L ?
          new Date(stage.scheduledTime as long).time : Long.MAX_VALUE
    } catch (Exception e) {
      e.printStackTrace()
    }
    return scheduledTime
  }
}
