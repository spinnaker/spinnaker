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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import rx.Observable
import rx.functions.Func1
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

@Slf4j
@Component
@ConditionalOnExpression(value = '${pollers.suspendedPipelines.enabled:false}')
class SuspendedPipelinesPollingNotificationAgent extends AbstractPollingNotificationAgent {

  static final String NOTIFICATION_TYPE = "suspendedPipeline"
  final String notificationType = NOTIFICATION_TYPE

  @Value('${pollers.suspendedPipelines.intervalMs:120000}')
  long pollingIntervalMs

  @Autowired
  ExecutionRepository executionRepository

  @Autowired
  SuspendedPipelinesPollingNotificationAgent(ObjectMapper objectMapper, Client jesqueClient) {
    super(objectMapper, jesqueClient)
  }

  @Override
  long getPollingInterval() {
    return pollingIntervalMs / 1000
  }

  @Override
  protected Func1<Execution, Boolean> filter() {
    return new Func1<Execution, Boolean>() {
      @Override
      Boolean call(Execution execution) {
        long now = new Date().time
        return execution.status == ExecutionStatus.SUSPENDED && execution.stages.find {
          now >= extractScheduledTime(it)
        }
      }
    }
  }

  @Override
  protected Observable<Execution> getEvents() {
    log.info("Starting Suspended Pipelines Polling Cycle")
    return executionRepository.retrieve(PIPELINE).doOnCompleted({
      log.info("Finished Suspended Pipelines Polling Cycle")
    })
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    return SuspendedPipelinesNotificationHandler
  }

  private static long extractScheduledTime(Stage stage) {
    long scheduledTime = Long.MAX_VALUE
    try {
      scheduledTime = stage.scheduledTime != null && stage.scheduledTime as long != 0L ?
        new Date(stage.scheduledTime as long).time : Long.MAX_VALUE
    } catch (Exception e) {
      log.error("Unable to extract scheduled time", e)
    }
    return scheduledTime
  }
}
