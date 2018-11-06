/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.discovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A component that starts doing something when the instance is up in discovery
 * and stops doing that thing when it goes down.
 */
public interface DiscoveryActivated extends ApplicationListener<RemoteStatusChangedEvent> {

  Logger log = LoggerFactory.getLogger(DiscoveryActivated.class);

  AtomicBoolean enabled = new AtomicBoolean();

  @Override
  default void onApplicationEvent(RemoteStatusChangedEvent event) {
    if (event.getSource().getStatus() ==  InstanceInfo.InstanceStatus.UP) {
      log.info("Instance is {}... {} starting", event.getSource().getStatus(), DiscoveryActivated.class.getSimpleName());
      enable();
    } else if (event.getSource().getPreviousStatus() == InstanceInfo.InstanceStatus.UP) {
      log.info("Instance is {}... {} stopping", event.getSource().getStatus(), DiscoveryActivated.class.getSimpleName());
      disable();
    }
  }

  default void enable() {
    enabled.set(true);
  }

  default void disable() {
    enabled.set(false);
  }
}
