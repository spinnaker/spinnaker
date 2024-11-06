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

package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.scheduler.actions.pipeline.PipelineConfigsPollingJob
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.PipelineTriggerJob
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.QuartzDiscoveryActivator
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.TriggerConverter
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.TriggerListener
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import org.quartz.JobDetail
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.quartz.JobDetailFactoryBean
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean

import javax.sql.DataSource
import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnExpression('${scheduler.enabled:false}')
@Import([DefaultSqlConfiguration, QuartzAutoConfiguration])
class SchedulerConfiguration {
  @Value('${scheduler.pipeline-configs-poller.polling-interval-ms:30000}')
  long syncInterval

  /**
   * Job for syncing pipeline triggers
   */
  @Bean
  JobDetailFactoryBean pipelineSyncJobBean(
    @Value('${scheduler.cron.timezone:America/Los_Angeles}') String timeZoneId
  ) {
    JobDetailFactoryBean syncJob = new JobDetailFactoryBean()
    syncJob.setJobClass(PipelineConfigsPollingJob.class)
    syncJob.jobDataMap.put("timeZoneId", timeZoneId)
    syncJob.setName("Sync Pipelines")
    syncJob.setGroup("Sync")
    syncJob.setDurability(true)

    return syncJob
  }

  /**
   * Trigger for the job to sync pipeline triggers
   */
  @Bean
  @ConditionalOnExpression('${scheduler.pipeline-configs-poller.enabled:true}')
  SimpleTriggerFactoryBean syncJobTriggerBean(
    @Value('${scheduler.pipeline-configs-poller.polling-interval-ms:60000}') long intervalMs,
    JobDetail pipelineSyncJobBean
  ) {
    SimpleTriggerFactoryBean triggerBean = new SimpleTriggerFactoryBean()

    triggerBean.setName("Sync Pipelines")
    triggerBean.setGroup("Sync")
    triggerBean.setStartDelay(TimeUnit.SECONDS.toMillis(60 + new Random().nextInt() % 60))
    triggerBean.setRepeatInterval(intervalMs)
    triggerBean.setJobDetail(pipelineSyncJobBean)

    return triggerBean
  }

  /**
   * Job for firing off a pipeline
   */
  @Bean
  JobDetailFactoryBean pipelineJobBean() {
    JobDetailFactoryBean triggerJob = new JobDetailFactoryBean()
    triggerJob.setJobClass(PipelineTriggerJob.class)
    triggerJob.setName(TriggerConverter.JOB_ID)
    triggerJob.setDurability(true)

    return triggerJob
  }

  @Bean
  SchedulerFactoryBeanCustomizer echoSchedulerFactoryBeanCustomizer(
    Optional<DataSource> dataSourceOptional,
    TriggerListener triggerListener,
    @Value('${sql.enabled:false}')
    boolean sqlEnabled
  ) {
    return new SchedulerFactoryBeanCustomizer() {
      @Override
      void customize(SchedulerFactoryBean schedulerFactoryBean) {
        if (dataSourceOptional.isPresent()) {
          schedulerFactoryBean.setDataSource(dataSourceOptional.get())
        }

        if (sqlEnabled) {
          Properties props = new Properties()
          props.put("org.quartz.jobStore.isClustered", "true")
          props.put("org.quartz.jobStore.acquireTriggersWithinLock", "true")
          props.put("org.quartz.scheduler.instanceId", "AUTO")
          props.put("org.quartz.jobStore.dontSetAutoCommitFalse", "false")
          schedulerFactoryBean.setQuartzProperties(props)
        }
        schedulerFactoryBean.setGlobalTriggerListeners(triggerListener)
      }
    }
  }

  @Bean
  QuartzDiscoveryActivator quartzDiscoveryActivator(SchedulerFactoryBean schedulerFactory) {
    return new QuartzDiscoveryActivator(schedulerFactory.getScheduler())
  }
}
