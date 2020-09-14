/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.credentials.poller;

import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.PeriodicTrigger;

@EnableConfigurationProperties(PollerConfigurationProperties.class)
@RequiredArgsConstructor
@Slf4j
public class PollerConfiguration implements SchedulingConfigurer {
  private final PollerConfigurationProperties config;
  private final List<AbstractCredentialsLoader<?>> pollers;

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    if (config.isEnabled()) {
      pollers.forEach(
          poller -> {
            PollerConfigurationProperties.Settings settings =
                config.getSettings(poller.getCredentialsRepository().getType());
            if (settings != null && settings.getReloadFrequencyMs() > 0) {
              PeriodicTrigger trigger =
                  new PeriodicTrigger(settings.getReloadFrequencyMs(), TimeUnit.MILLISECONDS);
              trigger.setInitialDelay(settings.getReloadFrequencyMs());
              taskRegistrar.addTriggerTask(new Poller<>(poller), trigger);
            }
          });
    }
  }
}
