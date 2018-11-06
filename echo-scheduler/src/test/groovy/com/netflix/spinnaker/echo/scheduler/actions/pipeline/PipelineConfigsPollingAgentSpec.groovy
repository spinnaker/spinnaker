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

package com.netflix.spinnaker.echo.scheduler.actions.pipeline

import com.netflix.scheduledactions.ActionInstance
import com.netflix.scheduledactions.ActionsOperator
import com.netflix.scheduledactions.triggers.CronTrigger
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.PipelineConfigsPollingAgent
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PipelineConfigsPollingAgentSpec extends Specification {

    Registry registry = new NoopRegistry()
    def actionsOperator = Mock(ActionsOperator)
    def pipelineCache = Mock(PipelineCache)
    @Subject pollingAgent = new PipelineConfigsPollingAgent(registry, pipelineCache, actionsOperator, 1_000_000, 'America/Los_Angeles')

    void 'when a new pipeline trigger is added, a scheduled action instance is registered with an id same as the trigger id'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(true)
            .type('cron')
            .cronExpression('* 0/30 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger])
        pipelineCache.getPipelinesSync() >> PipelineCache.decorateTriggers([pipeline])
        actionsOperator.getActionInstances() >> []

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        1 * actionsOperator.registerActionInstance(_ as ActionInstance)
    }

    @Unroll
    void 'with triggerEnabled=#triggerEnabled and pipelineDisabled=#pipelineDisabled, corresponding scheduled action is disabled'() {
        given:
        Trigger trigger = Trigger.builder()
            .enabled(triggerEnabled)
            .id('t1')
            .type(Trigger.Type.CRON.toString())
            .cronExpression('* 0/30 * * * ? *')
            .build()
        Pipeline pipeline = buildPipeline([trigger], pipelineDisabled)
        def decoratedPipelines = PipelineCache.decorateTriggers([pipeline]) // new id for trigger will be generated here
        ActionInstance actionInstance = buildScheduledAction(decoratedPipelines[0].triggers[0].id, '* 0/30 * * * ? *', true)
        pipelineCache.getPipelinesSync() >> decoratedPipelines
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        !actionInstance.disabled
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.enableActionInstance(_)
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.deleteActionInstance(_)
        1 * actionsOperator.disableActionInstance(actionInstance)

        where:
        triggerEnabled | pipelineDisabled
        false          | false
        true           | true
    }

    void 'when an existing pipeline trigger is removed, corresponding scheduled action is also removed'() {
        given:
        Pipeline pipeline = buildPipeline([])
        ActionInstance actionInstance = buildScheduledAction('t1', '* 0/30 * * * ? *', true)
        pipelineCache.getPipelinesSync() >> PipelineCache.decorateTriggers([pipeline])
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        0 * actionsOperator.updateActionInstance(_)
        0 * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        0 * actionsOperator.enableActionInstance(_)
        1 * actionsOperator.deleteActionInstance(actionInstance)
    }

    @Unroll
    void 'when we make changes to pipeline triggers, they are reflected in their corresponding action instance'() {
        given:
        def actionCron = '* 0/30 * * * ? *'

        Trigger trigger = Trigger.builder()
          .enabled(triggerEnabled)
          .id('t1')
          .type(Trigger.Type.CRON.toString())
          .cronExpression(changeTrigger ? actionCron.replaceAll('30', '45') : actionCron)
          .build()
        Pipeline pipeline = buildPipeline([trigger])
        def decoratedPipelines = PipelineCache.decorateTriggers([pipeline]) // new id for trigger will be generated here
        ActionInstance actionInstance = buildScheduledAction(changeTrigger ? trigger.id : decoratedPipelines[0].triggers[0].id, actionCron, actionEnabled)
        pipelineCache.getPipelinesSync() >> decoratedPipelines
        actionsOperator.getActionInstances() >> [actionInstance]

        when:
        pollingAgent.execute()

        then:
        numRegister * actionsOperator.registerActionInstance(_)
        0 * actionsOperator.disableActionInstance(_)
        numEnable * actionsOperator.enableActionInstance(_)
        numDelete * actionsOperator.deleteActionInstance(_)
        0 * actionsOperator.updateActionInstance(_ as ActionInstance)

        where:
        triggerEnabled | changeTrigger | actionEnabled || numRegister || numEnable || numDelete
        true           | true          | true          || 1           || 0         || 1  // existing trigger is updated -> deleted and created again
        false          | true          | false         || 0           || 0         || 1  // existing trigger is updated but disabled -> deleted and NOT created again
        true           | false         | false         || 0           || 1         || 0  // existing trigger is enabled -> action is enabled
        true           | false         | true          || 0           || 0         || 0  // no changes to pipeline trigger, no scheduled actions are updated
    }

    private static Pipeline buildPipeline(List<Trigger> triggers) {
        buildPipeline(triggers, false)
    }

    private static Pipeline buildPipeline(List<Trigger> triggers, boolean disabled) {
      Pipeline
        .builder()
        .application('api')
        .name('Pipeline 1')
        .id('p1')
        .parallel(true)
        .triggers(triggers)
        .disabled(disabled)
        .build()
    }

    private static ActionInstance buildScheduledAction(String id, String cronExpression, boolean enabled) {
        ActionInstance actionInstance = ActionInstance.newActionInstance()
            .withId(id)
            .withTrigger(new CronTrigger(cronExpression))
            .withParameters([triggerTimeZoneId: 'America/Los_Angeles'])
            .build()
        actionInstance.disabled = !enabled
        return actionInstance
    }
}
