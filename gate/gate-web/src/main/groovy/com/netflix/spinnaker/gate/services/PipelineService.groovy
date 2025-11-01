/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import com.netflix.spectator.api.patterns.IntervalCounter;
import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.security.AuthenticatedRequest
import de.huxhorn.sulky.ulid.ULID
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import jakarta.annotation.PostConstruct
import java.util.concurrent.TimeUnit

@Component
@Slf4j
class PipelineService {
  private static final String GROUP = "pipelines"

  private final ULID ulid = new ULID();

  @Autowired(required = false)
  Front50Service front50Service

  @Autowired(required = false)
  EchoService echoService

  @Autowired
  OrcaServiceSelector orcaServiceSelector

  @Autowired
  ApplicationService applicationService

  private final RetrySupport retrySupport = new RetrySupport()

  @Autowired
  Registry registry

  // Echo Event Metrics
  private IntervalCounter echoEventsIntervalCounter;
  private PercentileTimer echoEventsPercentileTimer;
  private IntervalCounter echoEventsErrorIntervalCounter;

  @PostConstruct
  public void postConstruct() {
    // Metrics for Echo Event handling.
    final String idPrefix = "echo.events";

    this.echoEventsIntervalCounter =
      IntervalCounter.get(this.registry, this.registry.createId(idPrefix + ".count"));
    this.echoEventsPercentileTimer =
      PercentileTimer.get(this.registry, this.registry.createId(idPrefix + ".duration"));
    this.echoEventsErrorIntervalCounter =
      IntervalCounter.get(this.registry, this.registry.createId(idPrefix + ".error"));
  }

  void deleteForApplication(String applicationName, String pipelineName) {
    Retrofit2SyncCall.execute(front50Service.deletePipelineConfig(applicationName, pipelineName))
  }

  void save(Map pipeline) {
    Retrofit2SyncCall.execute(front50Service.savePipelineConfig(pipeline))
  }

  Map update(String pipelineId, Map pipeline) {
    Retrofit2SyncCall.execute(front50Service.updatePipeline(pipelineId, pipeline))
  }

  void move(Map moveCommand) { //TODO: use update endpoint when front50 is live
    Retrofit2SyncCall.execute(front50Service.movePipelineConfig(moveCommand))
  }

  Map trigger(String application, String pipelineNameOrId, Map trigger) {
    def pipelineConfig = applicationService.getPipelineConfigForApplication(application, pipelineNameOrId)
    pipelineConfig.trigger = trigger
    if (trigger.notifications) {
      if (pipelineConfig.notifications) {
        pipelineConfig.notifications = (List) pipelineConfig.notifications + (List) trigger.notifications
      } else {
        pipelineConfig.notifications = trigger.notifications
      }
    }
    if (pipelineConfig.parameterConfig) {
      Map triggerParams = (Map) trigger.parameters ?: [:]
      pipelineConfig.parameterConfig.each { Map paramConfig ->
        String paramName = paramConfig.name
        if (paramConfig.required && paramConfig.default == null) {
          if (triggerParams[paramName] == null) {
            throw new IllegalArgumentException("Required parameter ${paramName} is missing")
          }
        }
      }
    }
    Retrofit2SyncCall.execute(orcaServiceSelector.select().startPipeline(pipelineConfig, trigger.user?.toString()))
  }

  Map triggerViaEcho(String application, String pipelineNameOrId, Map parameters) {
    def eventId = UUID.randomUUID()
    def executionId = ulid.nextValue().toString()
    parameters.put("eventId", eventId)
    parameters.put("executionId", executionId)

    // Note that the Gate generated UUID is used as the event id and set it at the top level of the Map.
    // This conforms to Event.java as used by Echo to deserialize the event upon receipt.
    // This also prevents Echo from generating yet another UUID.
    Map eventMap = [
      content: [
        application     : application,
        pipelineNameOrId: pipelineNameOrId,
        trigger         : parameters,
        user            : AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
      ],
      details: [
        type: "manual"
      ],
      eventId: eventId
    ]

    final long startTimeNanos = registry.clock().monotonicTime();

    try {
      Retrofit2SyncCall.execute(echoService.postEvent(eventMap))
    } catch (Exception e) {
      echoEventsErrorIntervalCounter.increment();
      log.error("Event processing failure: eventId={}, event={}", eventId, eventMap, e);
      throw(e)
    }

    // Echo Event Metrics
    final long durationInNanos = registry.clock().monotonicTime() - startTimeNanos;
    echoEventsIntervalCounter.increment();
    echoEventsPercentileTimer.record(durationInNanos, TimeUnit.NANOSECONDS);

    log.debug(
      "Event processing success: durationInNanos={}, eventId={}",
      durationInNanos, eventId);

    return [
      eventId: eventId,
      ref    : String.format("/pipelines/%s", executionId)
    ]
  }

  Map startPipeline(Map pipelineConfig, String user) {
    Retrofit2SyncCall.execute(orcaServiceSelector.select().startPipeline(pipelineConfig, user))
  }

  Map getPipeline(String id) {
    Retrofit2SyncCall.execute(orcaServiceSelector.select().getPipeline(id))
  }

  void cancelPipeline(String id, String reason, boolean force) {
    setApplicationForExecution(id)
    Retrofit2SyncCall.execute(orcaServiceSelector.select().cancelPipeline(id, reason, force, ""))
  }

  void pausePipeline(String id) {
    setApplicationForExecution(id)
    Retrofit2SyncCall.execute(orcaServiceSelector.select().pausePipeline(id, ""))
  }

  void resumePipeline(String id) {
    setApplicationForExecution(id)
    Retrofit2SyncCall.execute(orcaServiceSelector.select().resumePipeline(id, ""))
  }

  void deletePipeline(String id) {
    setApplicationForExecution(id)
    Retrofit2SyncCall.execute(orcaServiceSelector.select().deletePipeline(id))
  }

  Map updatePipelineStage(String executionId, String stageId, Map context) {
    setApplicationForExecution(executionId)
    Retrofit2SyncCall.execute(orcaServiceSelector.select().updatePipelineStage(executionId, stageId, context))
  }

  Map restartPipelineStage(String executionId, String stageId, Map context) {
    setApplicationForExecution(executionId)
    Retrofit2SyncCall.execute(orcaServiceSelector.select().restartPipelineStage(executionId, stageId, context))
  }

  Map evaluateExpressionForExecution(String executionId, String pipelineExpression) {
    Retrofit2SyncCall.execute(orcaServiceSelector.select().evaluateExpressionForExecution(executionId, pipelineExpression))
  }

  Map evaluateExpressionForExecutionAtStage(String executionId, String stageId, String pipelineExpression) {
    Retrofit2SyncCall.execute(orcaServiceSelector.select().evaluateExpressionForExecutionAtStage(executionId, stageId, pipelineExpression))
  }

  Map evaluateVariables(String executionId, String requisiteStageRefIds, String spelVersionOverride, List<Map<String, String>> expressions) {
    Retrofit2SyncCall.execute(orcaServiceSelector.select().evaluateVariables(executionId, requisiteStageRefIds, spelVersionOverride, expressions))
  }

  /**
   * Retrieve an orca execution by id to populate the application in AuthenticatedRequest
   *
   * @param id
   */
  void setApplicationForExecution(String id) {
    try {
      Map execution = retrySupport.retry({ -> getPipeline(id) }, 5, 1000, false)
      Object application = execution.get("application")
      if (application != null) {
        AuthenticatedRequest.setApplication(application.toString())
      }
    } catch (Exception e) {
      log.error("Error loading execution {} from orca", id, e)
    }
  }
}
