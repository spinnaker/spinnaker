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
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import static com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UNKNOWN;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;

public class NoDiscoveryApplicationStatusPublisher implements ApplicationListener<ContextRefreshedEvent> {
  private final ApplicationEventPublisher publisher;
  private final Logger log = LoggerFactory.getLogger(NoDiscoveryApplicationStatusPublisher.class);

  private static InstanceInfo.InstanceStatus instanceStatus = UNKNOWN;

  public NoDiscoveryApplicationStatusPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override public void onApplicationEvent(ContextRefreshedEvent event) {
    log.warn("No discovery client is available, assuming application is up");
    setInstanceStatus(UP);
  }

  private void setInstanceStatus(InstanceInfo.InstanceStatus current) {
    InstanceInfo.InstanceStatus previous = instanceStatus;
    instanceStatus = current;
    publisher.publishEvent(new RemoteStatusChangedEvent(new StatusChangeEvent(previous, current)));
  }

  public void setInstanceEnabled(boolean enabled) {
    setInstanceStatus(enabled ? UP : OUT_OF_SERVICE);
  }
}
