/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.spinnaker.kork.discovery.InstanceStatus;
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.context.ApplicationListener;

@Slf4j
public class QuartzDiscoveryActivator implements ApplicationListener<RemoteStatusChangedEvent> {
  private final Scheduler scheduler;

  public QuartzDiscoveryActivator(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    if (event.getSource().getStatus() == InstanceStatus.UP) {
      log.info("Instance is ${e.status}... resuming quartz scheduler");
      try {
        scheduler.start();
      } catch (SchedulerException e) {
        log.warn("Failed to resume quartz scheduler", e);
      }
    } else if (event.getSource().getPreviousStatus() == InstanceStatus.UP) {
      log.info("Instance is ${e.status}... placing quartz into standby");
      try {
        scheduler.standby();
      } catch (SchedulerException e) {
        log.warn("Failed to place quartz scheduler into standby", e);
      }
    }
  }
}
