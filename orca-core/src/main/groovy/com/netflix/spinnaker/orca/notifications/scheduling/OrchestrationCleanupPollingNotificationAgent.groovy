/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import rx.Observable
import rx.functions.Func1

@Slf4j
@Component
@ConditionalOnProperty(value = 'pollers.orchestrationCleanup.enabled')
class OrchestrationCleanupPollingNotificationAgent extends AbstractPollingNotificationAgent {
  static final String NOTIFICATION_TYPE = "orchestrationCleanup"
  final String notificationType = NOTIFICATION_TYPE

  @Autowired
  ExecutionRepository executionRepository

  @Value('${pollers.pipelineCleanup.intervalMs:86400000}')
  long pollingIntervalMs

  @Value('${pollers.pipelineCleanup.daysToKeep:7}')
  int daysToKeep

  @Autowired
  OrchestrationCleanupPollingNotificationAgent(ObjectMapper objectMapper, Client jesqueClient) {
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
        long cutoff = (new Date() - daysToKeep).time
        return execution.status == ExecutionStatus.SUCCEEDED && execution.startTime < cutoff
      }
    }
  }

  @Override
  protected Observable<Execution> getEvents() {
    log.info("Starting Orchestration Cleanup Polling Cycle")
    return executionRepository.retrieveOrchestrations().doOnCompleted({
      log.info("Finished Orchestration Cleanup Polling Cycle")
    })
  }

  @Override
  protected void notify(Map<String, ?> input) {
    try {
      def startTime = input.startTime ?: input.buildTime
      log.info("Deleting orchestration execution ${input.id} (startTime: ${startTime ? new Date(startTime as Long) : ""}, application: ${input.application})")
      executionRepository.deleteOrchestration(input.id as String)
    } catch (e) {
      log.error("Unable to delete orchestration execution ${input.id}", e)
    }
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    throw new UnsupportedOperationException()
  }
}
