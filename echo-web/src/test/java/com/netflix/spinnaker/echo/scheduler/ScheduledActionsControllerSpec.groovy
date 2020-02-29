package com.netflix.spinnaker.echo.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import org.quartz.CronTrigger
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.triggers.CronTriggerImpl
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import spock.lang.Shared
import spock.lang.Specification

class ScheduledActionsControllerSpec extends Specification {
  ObjectMapper objectMapper = EchoObjectMapper.getInstance()
  Scheduler scheduler = Mock(Scheduler)
  SchedulerFactoryBean schedulerFactoryBean = Mock(SchedulerFactoryBean)
  ScheduledActionsController sac

  @Shared CronTrigger trigger1 = makeTrigger("1", "America/New_York", true)
  @Shared CronTrigger trigger2 = makeTrigger("2", "America/Los_Angeles", false)

  void setup() {
    schedulerFactoryBean.scheduler >> scheduler
    sac = new ScheduledActionsController(schedulerFactoryBean)
  }

  void 'should get all triggers'() {
    given:
    def pipelineTriggerSet = new HashSet<TriggerKey>()
    def manualTriggerSet = new HashSet<TriggerKey>()

    pipelineTriggerSet.add(trigger1.key)
    manualTriggerSet.add(trigger2.key)

    scheduler.getTriggerKeys(_) >>> [pipelineTriggerSet, manualTriggerSet]
    scheduler.getTrigger(_) >>> [trigger1, trigger2]

    when:
    def result = sac.getAllScheduledActions()

    then:
    result.manuallyCreated.size() == 1
    result.pipeline.size() == 1

    result.pipeline[0].id == trigger1.key.name
    result.pipeline[0].application == trigger1.jobDataMap.getString("application")
    result.pipeline[0].pipelineId == trigger1.jobDataMap.getString("id")
    result.pipeline[0].cronExpression == trigger1.cronExpression
    result.pipeline[0].runAsUser == trigger1.jobDataMap.getString("runAsUser")
    result.pipeline[0].timezone == trigger1.timeZone.getID()
    result.pipeline[0].forceRebake == trigger1.jobDataMap.getBoolean("triggerRebake")

    result.manuallyCreated[0].id == trigger2.key.name
    result.manuallyCreated[0].application == trigger2.jobDataMap.getString("application")
    result.manuallyCreated[0].pipelineId == trigger2.jobDataMap.getString("id")
    result.manuallyCreated[0].cronExpression == trigger2.cronExpression
    result.manuallyCreated[0].runAsUser == trigger2.jobDataMap.getString("runAsUser")
    result.manuallyCreated[0].timezone == trigger2.timeZone.getID()
    result.manuallyCreated[0].forceRebake == trigger2.jobDataMap.getBoolean("triggerRebake")
  }

  void 'should fail creating a trigger with missing params'() {
    when:
    sac.createScheduledAction((TriggerDescription)objectMapper.readValue(payload, TriggerDescription.class))

    then:
    thrown(IllegalArgumentException)

    where:
    payload                                                                                    | _
    '{"id":"1", "application":"app", "pipelineId":"pipe"                                   }'  | _
    '{"id":"1", "application":"app",                      "cronExpression": "* * * * * * ?"}'  | _
    '{"id":"1",                      "pipelineId":"pipe", "cronExpression": "* * * * * * ?"}'  | _
    '{          "application":"app", "pipelineId":"pipe", "cronExpression": "* * * * * * ?"}'  | _
  }

  void 'should create a trigger with correct params'() {
    def payload = [
      id: "id1",
      application: "app",
      pipelineId: "pipe",
      cronExpression: "* 10 0/12 1/1 * ? *"
      ]

    when:
    def result = sac.createScheduledAction((TriggerDescription)objectMapper.convertValue(payload, TriggerDescription.class))

    then:
    1 * scheduler.scheduleJob(_ as Trigger) >> { t ->
      assert (t[0] instanceof CronTriggerImpl)
      CronTrigger trigger = t[0] as CronTrigger
      assert (trigger.key.name == payload.id)
      assert (trigger.cronExpression == payload.cronExpression)
      assert (trigger.timeZone == TimeZone.getDefault())
      assert (trigger.getJobDataMap().getString("id") == payload.pipelineId)
      assert (trigger.getJobDataMap().getString("application") == payload.application)
    }

    result == (TriggerDescription)objectMapper.convertValue(payload + [
      forceRebake: false,
      runAsUser: null,
      timezone: TimeZone.getDefault().getID()]
      , TriggerDescription.class)
  }

  void 'should delete trigger'() {
    scheduler.getTrigger(_) >> trigger1

    when:
    sac.deleteScheduledAction("key1")

    then:
    1 * scheduler.getTrigger(_ as TriggerKey) >> { args ->
      assert (args[0] as TriggerKey).equals(trigger1.key)

      return trigger1
    }

    1 * scheduler.unscheduleJob(_ as TriggerKey) >> { args ->
      assert (args[0] as TriggerKey).equals(trigger1.key)

      return true
    }
  }


  private CronTrigger makeTrigger(String id, String timezone, boolean rebake) {
    def trigger = new CronTriggerImpl(
      "key" + id, "user", "job","job",
      id + " 10 0/12 1/1 * ? *")
    trigger.timeZone = TimeZone.getTimeZone(timezone)

    trigger.jobDataMap.put("application", "app" + id)
    trigger.jobDataMap.put("id", "id" + id)
    trigger.jobDataMap.put("runAsUser", "runAsUser" + id)
    trigger.jobDataMap.put("timeZone", timezone)
    trigger.jobDataMap.put("triggerRebake", rebake)

    return trigger
  }
}
