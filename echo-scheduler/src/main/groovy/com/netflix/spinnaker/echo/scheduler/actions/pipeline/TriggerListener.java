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

import com.netflix.spectator.api.Registry;
import java.util.concurrent.TimeUnit;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.listeners.TriggerListenerSupport;
import org.springframework.stereotype.Component;

/**
 * Listener for triggers on the Quartz scheduler so we can log analytics e.g. time drift in cron
 * misfires NOTE: we use the default misfire policy which will only consider a trigger misfired if
 * it's delayed by more than a minute
 */
@Component
public class TriggerListener extends TriggerListenerSupport {
  private final Registry registry;

  public TriggerListener(Registry registry) {
    this.registry = registry;
  }

  @Override
  public String getName() {
    return TriggerListener.class.getName();
  }

  @Override
  public void triggerMisfired(Trigger trigger) {
    // Only track for actual pipeline trigger jobs
    if (trigger.getKey().getGroup() == Scheduler.DEFAULT_GROUP) {
      long misfireDeltaMs = System.currentTimeMillis() - trigger.getNextFireTime().getTime();
      registry.timer("echo.triggers.quartz.misfires").record(misfireDeltaMs, TimeUnit.MILLISECONDS);
    }
  }
}
