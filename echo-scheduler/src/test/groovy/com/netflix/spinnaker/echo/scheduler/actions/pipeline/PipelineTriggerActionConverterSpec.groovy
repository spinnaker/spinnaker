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
import com.netflix.scheduledactions.Context
import com.netflix.scheduledactions.triggers.CronTrigger
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.PipelineTriggerConverter
import rx.functions.Action1
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.PipelineTriggerConverter.isInSync

class PipelineTriggerActionConverterSpec extends Specification {
    @Shared
    def pipeline = Pipeline
      .builder()
      .application('api')
      .name('Test Pipeline')
      .id('789-1011')
      .parallel(true)
      .build()

    void 'toParameters() should return an equivalent map of parameters'() {
        setup:
        Trigger trigger = new Trigger(true, '123-456', 'cron', null, null, null, null, '* 0/30 * * * ? *')

        when:
        Map parameters = PipelineTriggerConverter.toParameters(pipeline, trigger)

        then:
        parameters.id == pipeline.id
        parameters.triggerId == trigger.id
        parameters.triggerType == trigger.type
        parameters.triggerCronExpression == trigger.cronExpression
        parameters.triggerEnabled == Boolean.toString(trigger.enabled)
    }

    void 'fromParameters() should return an equivalent valid Pipeline instance'() {
        setup:
        def pipelineCache = Mock(PipelineCache) {
          getPipelines() >> { [pipeline ]}
        }

        Map parameters = [
            id: '789-1011',
            triggerId: '123-456',
            triggerType: 'cron',
            triggerCronExpression: '* 0/30 * * * ? *',
            triggerEnabled: 'true'
        ]

        when:
        Pipeline pipelineWithTrigger = PipelineTriggerConverter.fromParameters(pipelineCache, parameters)

        then:
        pipelineWithTrigger.id == pipeline.id
        pipelineWithTrigger.name == pipeline.name
        pipelineWithTrigger.application == pipeline.application
        pipelineWithTrigger.trigger.id == parameters.triggerId
        pipelineWithTrigger.trigger.type == parameters.triggerType
        pipelineWithTrigger.trigger.cronExpression == parameters.triggerCronExpression
        pipelineWithTrigger.trigger.enabled == Boolean.valueOf(parameters.triggerEnabled)
    }

    void 'toScheduledAction() should return an equivalent valid ActionInstance'() {
        setup:
        Trigger trigger = new Trigger(true, '123-456', 'cron', null, null, null, null, '* 0/30 * * * ? *')

        when:
        ActionInstance actionInstance = PipelineTriggerConverter.toScheduledAction(pipeline, trigger)

        then:
        actionInstance.id == trigger.id
        actionInstance.name == 'Pipeline Trigger'
        actionInstance.group == pipeline.id
        actionInstance.action == PipelineTriggerAction.class
        actionInstance.trigger != null
        actionInstance.trigger instanceof CronTrigger
        ((CronTrigger) actionInstance.trigger).cronExpression == trigger.cronExpression
        actionInstance.parameters != null
        actionInstance.parameters.id == pipeline.id
        actionInstance.parameters.triggerId == trigger.id
        actionInstance.parameters.triggerType == trigger.type
        actionInstance.parameters.triggerCronExpression == trigger.cronExpression
        actionInstance.parameters.triggerEnabled == Boolean.toString(trigger.enabled)
    }

    void 'isInSync() should return true if cronExpression of the trigger and ActionInstance match'() {
        setup:
        Trigger trigger = Trigger.builder().cronExpression('* 0/30 * * * ? *').build()
        ActionInstance actionInstance = ActionInstance.newActionInstance()
            .withTrigger(new CronTrigger(trigger.cronExpression))
            .build()

        expect:
        isInSync(actionInstance, trigger)
    }

    void 'isInSync() should return true if trigger is not a cron trigger'() {
        setup:
        Trigger trigger = Trigger.builder().cronExpression('* 0/30 * * * ? *').build()
        ActionInstance actionInstance = ActionInstance.newActionInstance()
            .withTrigger(new com.netflix.scheduledactions.triggers.Trigger() {
                @Override
                void validate() throws IllegalArgumentException {}

                @Override
                com.netflix.fenzo.triggers.Trigger<Context> createFenzoTrigger(Context context,
                                                                               Class<? extends Action1<Context>> action) {
                    return null
                }
            })
            .build()

        expect:
        isInSync(actionInstance, trigger)
    }

}
