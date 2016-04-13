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

package com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl

import com.netflix.scheduledactions.ActionInstance
import com.netflix.scheduledactions.ActionsOperator
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.services.Front50Service
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.metrics.CounterService
import org.springframework.boot.actuate.metrics.GaugeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

/**
 * This component does the polling of pipeline configs and does the CRUD operations on scheduled-actions
 * as needed
 */
@Component
@ConditionalOnExpression('${scheduler.enabled:false} && ${scheduler.pipelineConfigsPoller.enabled:true}')
@Slf4j
class PipelineConfigsPollingAgent extends AbstractPollingAgent {
  public static final String TRIGGER_TYPE = "cron";

  private final CounterService counterService
  private final GaugeService gaugeService
  private final PipelineCache pipelineCache
  private final long intervalMs
  private final ActionsOperator actionsOperator
  private final String timeZoneId

  @Autowired
  PipelineConfigsPollingAgent(CounterService counterService,
                              GaugeService gaugeService,
                              PipelineCache pipelineCache,
                              ActionsOperator actionsOperator,
                              @Value('${scheduler.pipelineConfigsPoller.pollingIntervalMs:30000}') long intervalMs,
                              @Value('${scheduler.cron.timezone:America/Los_Angeles}') String timeZoneId) {
    super()
    this.counterService = counterService
    this.gaugeService = gaugeService
    this.pipelineCache = pipelineCache
    this.actionsOperator = actionsOperator
    this.intervalMs = intervalMs
    this.timeZoneId = timeZoneId
  }

  @Override
  String getName() {
    return PipelineConfigsPollingAgent.class.simpleName
  }

  @Override
  long getIntervalMs() {
    return intervalMs
  }

  @Override
  void execute() {
    long start = System.currentTimeMillis()
    try {
      log.info("Running the pipeline configs polling agent...")

      /**
       * Only interested in pipelines that have triggers and that too scheduled triggers
       */
      def pipelines = pipelineCache.getPipelines().findAll {
        it.triggers && it.triggers.any { TRIGGER_TYPE.equalsIgnoreCase(it.type) }
      }

      try {
        /**
         * Getting all the registered scheduled actions
         */
        List<ActionInstance> actionInstances = actionsOperator.getActionInstances()
        updateChangedTriggers(pipelines, actionInstances)
        registerNewTriggers(pipelines, actionInstances)
      } catch (Exception e) {
        counterService.increment("actionsOperator.list.errors")
        throw new Exception("Exception occurred while fetching all registered action instances", e)
      }
    } catch (Exception e) {
      log.error("Exception occurred in the execute() method of PipelineConfigsPollingAgent", e)
    } finally {
      gaugeService.submit("pipelineConfigsPollingAgent.executionTimeMillis", (double) System.currentTimeMillis() - start)
    }
  }

  void updateChangedTriggers(List<Pipeline> pipelines, List<ActionInstance> actionInstances) {
    try {
      /**
       * Extract ALL the triggers
       */
      List<Trigger> triggers = pipelines.collect { it.triggers }.flatten()

      /**
       * Iterate through all the registered ActionInstances and for each, check if the corresponding
       * trigger has been updated or not. If it is, then update the ActionInstance as well. Trigger and
       * ActionInstance have the same 'id'
       */
      if (actionInstances) {
        log.info("Found '${actionInstances?.size()}' existing scheduled action(s)...")
        log.info("Iterating through each scheduled action and checking if the corresponding trigger has been changed...")
      }

      actionInstances.each { actionInstance ->
        Trigger trigger = triggers.find { it.id == actionInstance.id }
        try {
          if (trigger) {
            if (trigger.enabled) {
              if (!PipelineTriggerConverter.isInSync(actionInstance, trigger, timeZoneId)) {
                /**
                 * The trigger has been updated, so we need to update the scheduled action
                 */
                log.info("Trigger '${trigger}' has been updated and enabled. Recreating the scheduled action...")
                Pipeline pipeline = pipelines.find { it.triggers.contains(trigger) }
                ActionInstance updatedActionInstance = PipelineTriggerConverter.toScheduledAction(pipeline,
                  trigger, timeZoneId)
                actionsOperator.updateActionInstance(updatedActionInstance)

                log.info("Successfully updated scheduled action '${actionInstance.id}' as the corresponding " +
                  "trigger has been updated")
              } else {
                if (actionInstance.disabled) {
                  actionsOperator.enableActionInstance(actionInstance)
                  log.info("Enabled scheduled action '${actionInstance.id}' as the corresponding trigger has " +
                    "been enabled")
                }
              }
            } else {
              if (!actionInstance.disabled) {
                actionsOperator.disableActionInstance(actionInstance)
                log.info("Disabled scheduled action '${actionInstance.id}' as the corresponding trigger has been " +
                  "disabled")
              }
            }
          } else {
            actionsOperator.deleteActionInstance(actionInstance)
            log.info("Removed scheduled action '${actionInstance.id}' as the corresponding trigger has been removed")
          }
        } catch (Exception e) {
          counterService.increment("updateTriggers.errors")
          log.error("Exception occurred while updating ${trigger}", e)
        }
      }
    } catch (Exception e) {
      log.error("Exception occurred updating existing triggers", e)
    }
  }

  void registerNewTriggers(List<Pipeline> pipelines, List<ActionInstance> actionInstances) {
    try {
      List<String> actionIds = actionInstances.collect { it.id }

      /**
       * Find all pipelines that don't have a trigger that is not associated (by id) with any actionInstance in the
       * list of actionInstances. This indicates that these are new triggers
       */
      List<Pipeline> pipelinesWithNewTriggers = pipelines.findAll {
        def cronTriggers = it.triggers.findAll { it.type == Trigger.Type.CRON.toString() }
        cronTriggers.any { !actionIds.contains(it.id) }
      }
      pipelinesWithNewTriggers.each { pipeline ->
        /**
         * Extract all the triggers of type 'cron' from a pipeline. Ideally, there would be just one,
         * but pipelines 'could' have multiple cron triggers
         */
        List<Trigger> triggers = pipeline.triggers.findAll {
          it.type == Trigger.Type.CRON.toString() && it.enabled
        }
        if (triggers) {
          log.info("Found '${triggers.size()}' new cron trigger(s) for ${pipeline}...")
        }
        triggers.each { trigger ->
          try {
            /**
             * Create an ActionInstance for each trigger and assign the same id as the trigger id
             * ActionInstances are grouped by pipeline id. Pass in enough parameters to the ActionInstance for it
             * to construct the Pipeline instance back
             */
            ActionInstance actionInstance = PipelineTriggerConverter.toScheduledAction(pipeline, trigger, timeZoneId)
            actionsOperator.registerActionInstance(actionInstance)
            log.info("Registered scheduled trigger '${actionInstance.id}' for ${trigger} of ${pipeline}")
            counterService.increment("newTriggers.count")

          } catch (Exception e) {
            counterService.increment("newTriggers.errors")
            log.error("Exception occurred while creating new ${trigger}", e)
          }
        }
      }
    } catch (Exception e) {
      log.error("Exception occurred while creating new triggers", e)
    }
  }

}
