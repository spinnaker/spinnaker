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

package com.netflix.spinnaker.orca.notifications.scheduling
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.functions.Func1

/**
 *
 */
@Component
class SuspendedPipelinesPollingNotificationAgent extends AbstractPollingNotificationAgent {

  static final String NOTIFICATION_TYPE = "suspendedPipeline"
  final long pollingInterval = 120
  final String notificationType = NOTIFICATION_TYPE

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  SuspendedPipelinesPollingNotificationAgent(ObjectMapper objectMapper, Client jesqueClient) {
    super(objectMapper, jesqueClient)
  }

  @Override
  protected List<Map> filterEvents(List<Map> pipelines) {
    long now = new Date().time
    return pipelines.findAll {
      it.status == ExecutionStatus.SUSPENDED.name() &&
      it.stages.find { Map stage -> now >= extractScheduledTime(stage) != null }
    } as List<Map>
  }

  @Override
  protected Func1<Long, List<Map>> getEvents() {
    return new Func1<Long, List<Map>>() {
      @Override
      List<Map> call(Long aLong) {
        List<Pipeline> pipelines = executionRepository.retrievePipelines()
        return objectMapper.convertValue(pipelines, new TypeReference<List<Map>>() { })
      }
    }
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    return SuspendedPipelinesNotificationHandler
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
