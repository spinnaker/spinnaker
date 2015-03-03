/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.eureka;

import com.netflix.discovery.StatusChangeEvent;
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UNKNOWN;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;

public class NoDiscoveryApplicationStatusPublisher implements ApplicationListener<ContextRefreshedEvent> {

  private final ApplicationContext context;
  private final Logger log = LoggerFactory.getLogger(NoDiscoveryApplicationStatusPublisher.class);

  private static final EurekaStatusChangedEvent DEFAULT_UP_EVENT = new EurekaStatusChangedEvent(new StatusChangeEvent(UNKNOWN, UP));

  public NoDiscoveryApplicationStatusPublisher(ApplicationContext context) {
    this.context = context;
  }

  @Override public void onApplicationEvent(ContextRefreshedEvent event) {
    log.warn("No discovery client is available, assuming application is up");
    context.publishEvent(DEFAULT_UP_EVENT);
  }
}
