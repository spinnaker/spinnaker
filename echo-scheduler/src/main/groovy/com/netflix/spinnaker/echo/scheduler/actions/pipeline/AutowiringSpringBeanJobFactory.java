/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.echo.scheduler.actions.pipeline;

import org.quartz.Job;
import org.quartz.SchedulerContext;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Taken from https://dzone.com/articles/spring-and-quartz-integration-that-works-together
 *
 * <p>Allows us to have Quartz jobs play nicely with Spring DI
 */
public final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory
    implements ApplicationContextAware {
  private ApplicationContext ctx;
  private SchedulerContext schedulerContext;

  @Override
  public void setApplicationContext(final ApplicationContext context) {
    this.ctx = context;
  }

  @Override
  protected Object createJobInstance(final TriggerFiredBundle bundle) {
    Job job = ctx.getBean(bundle.getJobDetail().getJobClass());
    BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(job);
    MutablePropertyValues pvs = new MutablePropertyValues();
    pvs.addPropertyValues(bundle.getJobDetail().getJobDataMap());
    pvs.addPropertyValues(bundle.getTrigger().getJobDataMap());
    if (this.schedulerContext != null) {
      pvs.addPropertyValues(this.schedulerContext);
    }
    bw.setPropertyValues(pvs, true);
    return job;
  }

  public void setSchedulerContext(SchedulerContext schedulerContext) {
    this.schedulerContext = schedulerContext;
    super.setSchedulerContext(schedulerContext);
  }
}
