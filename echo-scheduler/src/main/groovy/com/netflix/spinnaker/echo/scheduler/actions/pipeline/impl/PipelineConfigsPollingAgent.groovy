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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.*

/**
 * This component does the polling of pipeline configs and does the CRUD operations on scheduled-actions
 * as needed
 */
@Component
@ConditionalOnExpression('${scheduler.enabled:false} && ${scheduler.pipelineConfigsPoller.enabled:true}')
@Slf4j
class PipelineConfigsPollingAgent extends AbstractPollingAgent {
  public static final String TRIGGER_TYPE = "cron";

  private final Registry registry
  private final PipelineCache pipelineCache
  private final long intervalMs
  private final ActionsOperator actionsOperator
  private final String timeZoneId

  @Autowired
  PipelineConfigsPollingAgent(Registry registry,
                              PipelineCache pipelineCache,
                              ActionsOperator actionsOperator,
                              @Value('${scheduler.pipelineConfigsPoller.pollingIntervalMs:30000}') long intervalMs,
                              @Value('${scheduler.cron.timezone:America/Los_Angeles}') String timeZoneId) {
    super()
    this.registry = registry
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

      // Only interested in pipelines that have triggers and that too scheduled triggers
      def pipelines = pipelineCache.getPipelines().findAll {
        it.triggers && it.triggers.any { TRIGGER_TYPE.equalsIgnoreCase(it.type) }
      }

      def triggerRepo = new TriggerRepository(pipelines)

      try {
        /**
         * Getting all the registered scheduled actions
         */
        List<ActionInstance> actionInstances = actionsOperator.getActionInstances()
        updateChangedTriggers(triggerRepo, actionInstances)
        registerNewTriggers(triggerRepo, actionInstances)
      } catch (Exception e) {
        registry.counter("actionsOperator.list.errors").increment()
        throw new Exception("Exception occurred while fetching all registered action instances", e)
      }
    } catch (Exception e) {
      log.error("Exception occurred in the execute() method of PipelineConfigsPollingAgent", e)
    } finally {
      long elapsedMillis = System.currentTimeMillis() - start
      log.info("Done polling for pipeline configs in ${elapsedMillis/1000}s")
      registry.timer("pipelineConfigsPollingAgent.executionTimeMillis").record(elapsedMillis, TimeUnit.MILLISECONDS)
    }
  }

  void updateChangedTriggers(TriggerRepository triggerRepo, List<ActionInstance> actionInstances) {
    try {
      /**
       * Iterate through all the registered ActionInstances and for each, check if the corresponding
       * trigger has been updated or not. If it is, then update the ActionInstance as well. Trigger and
       * ActionInstance have the same 'id'
       */
      if (actionInstances) {
        log.info("Found ${actionInstances?.size()} existing scheduled action(s). " +
          "Iterating through each scheduled action and checking if the corresponding trigger has been changed...")
      }

      actionInstances.each { actionInstance ->
        // InMemoryActionInstanceDao builds composite action instance ids that end with the trigger id
        // e.g. 2d05822d-0275-454b-9616-361bf3b557ca:com.netflix.scheduledactions.ActionInstance:74f13df7-e642-4f8b-a5f2-0d5319aa0bd1
        Trigger trigger = triggerRepo.getTrigger(actionInstance.id)
        try {
          if (trigger) {
            Pipeline pipeline = trigger.parent
            if (trigger.enabled && !pipeline.disabled) {
              if (!PipelineTriggerConverter.isInSync(actionInstance, trigger, timeZoneId)) {
                /**
                 * The trigger has been updated, so we need to update the scheduled action
                 */
                log.info("Trigger '${trigger}' has been updated and enabled. Recreating the scheduled action...")
                ActionInstance updatedActionInstance = PipelineTriggerConverter.toScheduledAction(pipeline,
                  trigger, timeZoneId)

                if (updatedActionInstance.id != actionInstance.id) {
                  updatedActionInstance.id = actionInstance.id
                }

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
                log.info("Disabled scheduled action '${actionInstance.id}' as the corresponding trigger ${trigger} has been " +
                  "disabled")
              }
            }
          } else {
            actionsOperator.deleteActionInstance(actionInstance)
            log.info("Removed scheduled action '${actionInstance.id}' as the corresponding trigger has been removed")
          }
        } catch (Exception e) {
          registry.counter("updateTriggers.errors", "exception", e.getClass().getName()).increment()
          log.error("Exception occurred while updating ${trigger}", e)
        }
      }
    } catch (Exception e) {
      log.error("Exception occurred updating existing triggers", e)
    }
  }

  void registerNewTriggers(TriggerRepository triggerRepo, List<ActionInstance> actionInstances) {
    try {
      // find all the triggers that don't have a corresponding action instance (by id)
      // this indicates that these are new triggers
      actionInstances.each { actionInstance ->
        triggerRepo.remove(actionInstance.id)
      }

      Collection<Trigger> newTriggers = triggerRepo.triggers()
      newTriggers.each { trigger ->
        if (!Trigger.Type.CRON.toString().equalsIgnoreCase(trigger.type) || !trigger.enabled) {
          log.debug("Skipping disabled or non-cron trigger ${trigger}")
          return
        }

        Pipeline pipeline = trigger.parent
        if (pipeline.disabled) {
          log.debug("Skipping disabled pipeline ${pipeline}")
          return
        }

        try {
          // Create an ActionInstance for each trigger and assign the same id as the trigger id
          // ActionInstances are grouped by pipeline id. Pass in enough parameters to the ActionInstance for it
          // to construct the Pipeline instance back
          ActionInstance actionInstance = PipelineTriggerConverter.toScheduledAction(pipeline, trigger, timeZoneId)
          actionsOperator.registerActionInstance(actionInstance)
          log.info('Registered scheduled trigger {} {} {}', kv('id', actionInstance.id), kv('trigger', trigger), kv('pipeline', pipeline))
          registry.counter("newTriggers.count").increment()
        } catch (Exception e) {
          log.error("Exception occurred while creating new trigger ${trigger} for pipeline ${pipeline}", e)
          registry.counter("newTriggers.errors", "exception", e.getClass().getName()).increment()
        }
      }
    } catch (Exception e) {
      log.error("Exception occurred while creating new triggers", e)
    }
  }
}
