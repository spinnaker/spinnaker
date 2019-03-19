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

import com.netflix.spinnaker.echo.scheduler.actions.pipeline.AutowiringSpringBeanJobFactory
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.PipelineConfigsPollingJob
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.PipelineTriggerJob
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.TriggerListener
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.TriggerConverter
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.squareup.okhttp.OkHttpClient
import org.quartz.JobDetail
import org.quartz.Trigger
import org.quartz.spi.JobFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.quartz.JobDetailFactoryBean
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean
import retrofit.client.Client
import retrofit.client.OkClient
import javax.sql.DataSource
import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnExpression('${scheduler.enabled:false}')
@Import(DefaultSqlConfiguration)
class SchedulerConfiguration {
  @Bean
  SchedulerFactoryBean schedulerFactoryBean(
    Optional<DataSource> dataSourceOptional,
    TriggerListener triggerListener,
    JobDetail pipelineJobBean,
    JobFactory jobFactory,
    Optional<Trigger> syncJobTrigger
  ) {
    SchedulerFactoryBean factoryBean = new SchedulerFactoryBean()
    if (dataSourceOptional.isPresent()) {
      factoryBean.dataSource = dataSourceOptional.get()
    }

    factoryBean.setGlobalTriggerListeners(triggerListener)
    factoryBean.setJobDetails(pipelineJobBean)
    factoryBean.setJobFactory(jobFactory)

    if (syncJobTrigger.isPresent()) {
      factoryBean.setTriggers(syncJobTrigger.get())
    }

    return factoryBean
  }

  /**
   * Job factory used to create jobs as beans on behalf of Quartz
   */
  @Bean
  JobFactory jobFactory(ApplicationContext applicationContext) {
    AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory()
    jobFactory.setApplicationContext(applicationContext)

    return jobFactory
  }

  /**
   * Job for syncing pipeline triggers
   */
  @Bean
  JobDetailFactoryBean pipelineSyncJobBean(
    @Value('${scheduler.cron.timezone:America/Los_Angeles}') String timeZoneId
  ) {
    JobDetailFactoryBean syncJob = new JobDetailFactoryBean()
    syncJob.jobClass = PipelineConfigsPollingJob.class
    syncJob.jobDataMap.put("timeZoneId", timeZoneId)
    syncJob.name = "Sync Pipelines"
    syncJob.group = "Sync"

    return syncJob
  }

  /**
   * Trigger for the job to sync pipeline triggers
   */
  @Bean
  @ConditionalOnExpression('${scheduler.pipelineConfigsPoller.enabled:true}')
  SimpleTriggerFactoryBean syncJobTriggerBean(
    @Value('${scheduler.pipelineConfigsPoller.pollingIntervalMs:30000}') long intervalMs,
    JobDetail pipelineSyncJobBean
  ) {
    SimpleTriggerFactoryBean triggerBean = new SimpleTriggerFactoryBean()

    triggerBean.name = "Sync Pipelines"
    triggerBean.group = "Sync"
    triggerBean.startDelay = 60 * 1000
    triggerBean.repeatInterval = intervalMs
    triggerBean.jobDetail = pipelineSyncJobBean

    return triggerBean
  }

  /**
   * Job for firing off a pipeline
   */
  @Bean
  JobDetailFactoryBean pipelineJobBean() {
    JobDetailFactoryBean triggerJob = new JobDetailFactoryBean()
    triggerJob.jobClass = PipelineTriggerJob.class
    triggerJob.name = TriggerConverter.JOB_ID
    triggerJob.durability = true

    return triggerJob
  }

  @Bean
  Client retrofitClient(@Value('${retrofit.connectTimeoutMillis:10000}') long connectTimeoutMillis,
                        @Value('${retrofit.readTimeoutMillis:15000}') long readTimeoutMillis) {
    OkHttpClient okHttpClient = new OkHttpClient()
    okHttpClient.setConnectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
    okHttpClient.setReadTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
    new OkClient(okHttpClient)
  }
}
