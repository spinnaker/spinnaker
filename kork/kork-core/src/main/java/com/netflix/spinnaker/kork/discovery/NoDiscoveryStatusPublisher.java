/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/** Provides default discovery status (UP) if no discovery status publisher can be found. */
public class NoDiscoveryStatusPublisher
    implements DiscoveryStatusPublisher, ApplicationListener<ContextRefreshedEvent> {

  private static final Logger log = LoggerFactory.getLogger(NoDiscoveryStatusPublisher.class);

  private final ApplicationEventPublisher eventPublisher;

  public NoDiscoveryStatusPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    log.warn("No service discovery client is available, assuming application is UP");
    setInstanceStatus(InstanceStatus.UP);
  }

  public void setInstanceEnabled(boolean enabled) {
    setInstanceStatus(enabled ? InstanceStatus.UP : InstanceStatus.OUT_OF_SERVICE);
  }

  private void setInstanceStatus(InstanceStatus instanceStatus) {
    InstanceStatus previousStatus =
        instanceStatus == InstanceStatus.UP ? InstanceStatus.UNKNOWN : InstanceStatus.UP;
    eventPublisher.publishEvent(
        new RemoteStatusChangedEvent(
            new DiscoveryStatusChangeEvent(previousStatus, instanceStatus)));
  }
}
