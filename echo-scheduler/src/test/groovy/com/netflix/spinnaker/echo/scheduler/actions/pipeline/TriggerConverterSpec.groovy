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

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import org.quartz.CronScheduleBuilder
import org.quartz.CronTrigger
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static TriggerConverter.isInSync

class TriggerConverterSpec extends Specification {
    @Shared
    def pipeline = Pipeline
      .builder()
      .application('api')
      .name('Test Pipeline')
      .id('789-1011')
      .parallel(true)
      .build()

    @Unroll
    void 'toParameters() should return an equivalent map of parameters with triggerId=#triggerId'() {
        setup:
        Trigger trigger = Trigger.builder()
            .id(triggerId)
            .type('cron')
            .cronExpression('* 0/30 * * * ? *')
            .rebake(triggerRebake)
            .runAsUser("mr.captain")
            .parent(pipeline)
            .build()

        when:
        Map parameters = TriggerConverter.toParamMap(trigger, 'America/New_York')

        then:
        parameters.id == pipeline.id
        parameters.triggerId == trigger.id
        parameters.triggerType == trigger.type
        parameters.triggerCronExpression == trigger.cronExpression
        parameters.triggerTimeZoneId == 'America/New_York'
        parameters.triggerRebake == Boolean.toString(trigger.rebake)
        parameters.runAsUser == 'mr.captain'

        where:
        triggerId << ['123-456', null]
        triggerRebake << [true, false]
    }

    @Unroll
    void 'fromParameters() should return an equivalent valid Pipeline instance'() {
        setup:
        def pipelineCache = Mock(PipelineCache) {
          getPipelinesSync() >> [pipeline]
        }

        Map parameters = [
            id: '789-1011',
            triggerId: '123-456',
            triggerType: 'cron',
            triggerCronExpression: '* 0/30 * * * ? *',
            triggerEnabled: "true",
            triggerRebake: triggerRebake
        ]

        when:
        Pipeline pipelineWithTrigger = TriggerConverter.toPipeline(pipelineCache, parameters)

        then:
        pipelineWithTrigger.id == pipeline.id
        pipelineWithTrigger.name == pipeline.name
        pipelineWithTrigger.application == pipeline.application
        pipelineWithTrigger.trigger.id == parameters.triggerId
        pipelineWithTrigger.trigger.type == parameters.triggerType
        pipelineWithTrigger.trigger.cronExpression == parameters.triggerCronExpression
        pipelineWithTrigger.trigger.enabled == Boolean.valueOf(parameters.triggerEnabled)
        pipelineWithTrigger.trigger.rebake == Boolean.valueOf(parameters.triggerRebake)

        where:
        triggerRebake << ['true', 'false']
    }

    @Unroll
    void 'toQuartzTrigger() should return an equivalent valid ActionInstance with triggerId=#triggerId'() {
        setup:
        Trigger trigger = Trigger.builder()
            .id(triggerId)
            .type('cron')
            .cronExpression('* 0/30 * * * ? *')
            .parent(pipeline)
            .build()

        when:
        org.quartz.Trigger triggerInstance = TriggerConverter.toQuartzTrigger(
          trigger,
          TimeZone.getTimeZone('America/Los_Angeles'))

        then:
        triggerInstance.key.name == trigger.id
        triggerInstance.key.group == PipelineConfigsPollingJob.PIPELINE_TRIGGER_GROUP_PREFIX + trigger.parent.id
        triggerInstance.jobKey.name == TriggerConverter.JOB_ID
        triggerInstance instanceof CronTrigger
        ((CronTrigger) triggerInstance).cronExpression == trigger.cronExpression
        triggerInstance.jobDataMap.getString("id") == pipeline.id
        triggerInstance.jobDataMap.getString("triggerId") == trigger.id
        triggerInstance.jobDataMap.getString("triggerType") == trigger.type
        triggerInstance.jobDataMap.getString("triggerCronExpression") == trigger.cronExpression
        triggerInstance.jobDataMap.getString("triggerTimeZoneId") == 'America/Los_Angeles'

        where:
        triggerId << ['123-456']
    }

    @Unroll
    void 'isInSync() should return true if cronExpression, timezone of the trigger, and runAsUser match the ActionInstance'() {
        setup:
        Trigger pipelineTrigger = Trigger.builder()
                                 .id("id1")
                                 .parent(pipeline)
                                 .type(Trigger.Type.CRON.toString())
                                 .cronExpression('* 0/30 * * * ? *')
                                 .runAsUser("batman")
                                 .build()
        org.quartz.Trigger scheduleTrigger = org.quartz.TriggerBuilder.newTrigger()
          .withIdentity("ignored", null)
          .withSchedule(CronScheduleBuilder.cronSchedule(pipelineTrigger.cronExpression)
            .inTimeZone(TimeZone.getTimeZone(actionInstanceTimeZoneId)))
          .usingJobData("runAsUser", runAsUser)
          .build()

        expect:
        isInSync(scheduleTrigger, pipelineTrigger, TimeZone.getTimeZone(currentTimeZoneId)) == expectedInSync

        where:
        actionInstanceTimeZoneId | currentTimeZoneId  | runAsUser | expectedInSync
        'America/New_York'       | 'America/New_York' | 'batman'  | true
        'America/Los_Angeles'    | 'America/New_York' | 'batman'  | false
        ''                       | 'America/New_York' | 'batman'  | false
        'America/New_York'       | 'America/New_York' | 'robin'   | false
    }
}
