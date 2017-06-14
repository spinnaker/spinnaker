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

package com.netflix.spinnaker.orca.restart

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import net.greghaines.jesque.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import rx.Observable
import rx.functions.Func1
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static java.util.concurrent.TimeUnit.MINUTES

/**
 * Detects pipelines that were running on another Orca instance that no longer exists and enqueues them for restart.
 */
@Deprecated
@Component
@ConditionalOnExpression('${pollers.stalePipelines.enabled:false}')
@Slf4j
@CompileStatic
class PipelineRestartAgent extends AbstractPollingNotificationAgent {

  public static final String NOTIFICATION_TYPE = "stalePipeline"

  private final ExecutionRepository executionRepository
  private final InstanceStatusProvider instanceStatusProvider
  private final String applicationName
  private final String currentInstanceId

  @Autowired
  PipelineRestartAgent(ObjectMapper mapper, Client jesqueClient, ExecutionRepository executionRepository, InstanceStatusProvider instanceStatusProvider,
                       String currentInstanceId, @Value('${spring.application.name:orca}') String applicationName) {
    super(mapper, jesqueClient)
    this.executionRepository = executionRepository
    this.instanceStatusProvider = instanceStatusProvider
    log.info "current instance: ${applicationName} ${currentInstanceId}"
    this.applicationName = applicationName
    this.currentInstanceId = currentInstanceId
  }

  @Override
  long getPollingInterval() {
    return MINUTES.toSeconds(2)
  }

  @Override
  String getNotificationType() {
    NOTIFICATION_TYPE
  }

  @Override
  @CompileDynamic
  protected Observable<Execution> getEvents() {
    log.info("Starting stale pipelines polling cycle")
    return executionRepository.retrievePipelines().doOnCompleted({
      log.info("Finished stale pipelines polling cycle")
    })
  }

  @Override
  protected Func1<Execution, Boolean> filter() {
    return { Execution execution ->
      if (execution?.status in [NOT_STARTED, RUNNING]) {
        if (!execution.executingInstance) {
          log.info "Pipeline $execution.application $execution.id is $execution.status but it has no record of its executing instance (old pipeline)"
          return false
        } else if (execution.executingInstance == currentInstanceId) {
          log.info "Pipeline $execution.application $execution.id is $execution.status but it is already running on this instance"
          return false
        } else if (executingInstanceIsDown(execution)) {
          log.info "Pipeline $execution.application $execution.id is $execution.status and its instance is down $execution.executingInstance"
          return true
        } else {
          log.info "Pipeline $execution.application $execution.id is $execution.status but its instance is up $execution.executingInstance"
          return false
        }
      } else {
        return false
      }
    } as Func1<Execution, Boolean>
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    PipelineRestartHandler
  }

  private boolean executingInstanceIsDown(Execution execution) {
    def instanceId = execution.executingInstance
    return !instanceStatusProvider.isInstanceUp(applicationName, instanceId)
  }
}
