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

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;

public class DiscoveryStatusListener implements ApplicationListener<RemoteStatusChangedEvent> {

  private static final Logger log = LoggerFactory.getLogger(DiscoveryStatusListener.class);

  private final AtomicBoolean enabled;

  public DiscoveryStatusListener() {
    this(false);
  }

  /**
   * To support unit tests, specifies the initial state of the DiscoveryStatusListener.
   *
   * @param enabledInitialState the initial enabled state of this DiscoveryStatusListener prior to
   *     receiving any RemoteStatusChangedEvents.
   */
  public DiscoveryStatusListener(boolean enabledInitialState) {
    this.enabled = new AtomicBoolean(enabledInitialState);
  }

  @Override
  public void onApplicationEvent(RemoteStatusChangedEvent event) {
    log.info(
        "Instance status has changed to {} in service discovery", event.getSource().getStatus());

    if (event.getSource().getStatus() == InstanceStatus.UP) {
      enabled.set(true);
    } else if (event.getSource().getPreviousStatus() == InstanceStatus.UP) {
      enabled.set(false);
    }
  }

  public boolean isEnabled() {
    return enabled.get();
  }
}
