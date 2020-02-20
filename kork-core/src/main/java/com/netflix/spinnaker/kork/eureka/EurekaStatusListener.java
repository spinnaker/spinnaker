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
 */

package com.netflix.spinnaker.kork.eureka;

import com.netflix.appinfo.InstanceInfo;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;

/**
 * A component that listens to Eureka status changes and can be queried to enable/disable components
 */
public class EurekaStatusListener implements ApplicationListener<RemoteStatusChangedEvent> {
  private static final Logger log = LoggerFactory.getLogger(EurekaStatusListener.class);

  private AtomicBoolean enabled = new AtomicBoolean();

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    log.info("Instance status has changed to {} in Eureka", event.getSource().getStatus());

    if (event.getSource().getStatus() == InstanceInfo.InstanceStatus.UP) {
      enabled.set(true);
    } else if (event.getSource().getPreviousStatus() == InstanceInfo.InstanceStatus.UP) {
      enabled.set(false);
    }
  }

  public boolean isEnabled() {
    return enabled.get();
  }
}
