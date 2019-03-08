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

package com.netflix.spinnaker.echo.scheduler;

import com.netflix.spinnaker.echo.cron.CronExpressionFuzzer;
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.PipelineConfigsPollingJob;
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.TriggerConverter;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.omg.CosNaming.NamingContextPackage.NotEmpty;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.web.bind.annotation.*;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.ArrayList;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.TriggerKey.triggerKey;

@RestController
@ConditionalOnExpression("${scheduler.enabled:false}")
public class ScheduledActionsController {
  private static final String USER_TRIGGER_GROUP = "user";

  private Scheduler scheduler;

  @Autowired
  public ScheduledActionsController(SchedulerFactoryBean schedulerBean) {
    this.scheduler = schedulerBean.getScheduler();
  }

  @RequestMapping(value = "/scheduledActions", method = RequestMethod.GET)
  public TriggerListResponse getAllScheduledActions() throws SchedulerException {
    Set<TriggerKey> triggerKeys = scheduler.getTriggerKeys(
      GroupMatcher.triggerGroupStartsWith(PipelineConfigsPollingJob.PIPELINE_TRIGGER_GROUP_PREFIX));

    Set<TriggerKey> manuallyCreatedKeys = scheduler.getTriggerKeys(
      GroupMatcher.triggerGroupEquals(USER_TRIGGER_GROUP));

    ArrayList<TriggerDescription> pipelineTriggers = new ArrayList<>(triggerKeys.size());
    ArrayList<TriggerDescription> manualTriggers = new ArrayList<>(manuallyCreatedKeys.size());

    for (TriggerKey triggerKey : triggerKeys) {
      pipelineTriggers.add(toTriggerDescription((CronTrigger)scheduler.getTrigger(triggerKey)));
    }

    for (TriggerKey triggerKey : manuallyCreatedKeys) {
      manualTriggers.add(toTriggerDescription((CronTrigger)scheduler.getTrigger(triggerKey)));
    }

    return new TriggerListResponse(pipelineTriggers, manualTriggers);
  }

  @RequestMapping(value = "/scheduledActions", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.CREATED)
  public TriggerDescription createScheduledAction(@RequestBody TriggerDescription newTrigger) throws SchedulerException {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Set<ConstraintViolation<TriggerDescription>> violations = validator.validate(newTrigger);

    if (violations.size() > 0) {
      String errorMessage = violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(","));
      throw new IllegalArgumentException(errorMessage);
    }

    org.quartz.CronTrigger trigger = (CronTrigger) scheduler.getTrigger(
      triggerKey(newTrigger.getId(), USER_TRIGGER_GROUP));

    if (trigger != null) {
      throw new IllegalArgumentException("Trigger with id: " + newTrigger.getId() + " already exists");
    }

    TimeZone timeZone = TimeZone.getDefault();

    if (newTrigger.getTimezone() != null) {
      timeZone = TimeZone.getTimeZone(newTrigger.getTimezone());
    }

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put("id", newTrigger.getPipelineId());
    jobDataMap.put("application", newTrigger.getApplication());
    jobDataMap.put("triggerId", newTrigger.getId());
    jobDataMap.put("triggerCronExpression", newTrigger.getCronExpression());
    jobDataMap.put("triggerTimeZoneId", timeZone.getID());
    jobDataMap.put("triggerRebake", newTrigger.getForceRebake());
    jobDataMap.put("runAsUser", newTrigger.getRunAsUser());

    trigger = TriggerBuilder.newTrigger()
      .withIdentity(newTrigger.getId(), USER_TRIGGER_GROUP)
      .withSchedule(cronSchedule(CronExpressionFuzzer.fuzz(newTrigger.getId(), newTrigger.getCronExpression()))
        .inTimeZone(timeZone))
      .usingJobData(jobDataMap)
      .forJob(TriggerConverter.JOB_ID)
      .build();

    scheduler.scheduleJob(trigger);

    return toTriggerDescription(trigger);
  }

  @RequestMapping(value = "/scheduledActions/{id}", method = RequestMethod.DELETE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public TriggerDescription deleteScheduledAction(@PathVariable String id) throws SchedulerException {
    org.quartz.CronTrigger trigger = (CronTrigger)scheduler.getTrigger(triggerKey(id, USER_TRIGGER_GROUP));

    if (trigger == null) {
      throw new NotFoundException("Trigger with id: " + id + " not be found");
    }

    TriggerDescription description = toTriggerDescription(trigger);
    scheduler.unscheduleJob(trigger.getKey());

    return description;
  }

  private TriggerDescription toTriggerDescription(CronTrigger trigger) {
    TriggerDescription description = new TriggerDescription();
    description.setId(trigger.getKey().getName());
    description.setApplication(trigger.getJobDataMap().getString("application"));
    description.setCronExpression(trigger.getCronExpression());
    description.setPipelineId(trigger.getJobDataMap().getString("id"));
    description.setRunAsUser(trigger.getJobDataMap().getString("runAsUser"));
    description.setTimezone(trigger.getTimeZone().getID());
    description.setForceRebake(trigger.getJobDataMap().getBooleanValue("triggerRebake"));

    return description;
  }
}
