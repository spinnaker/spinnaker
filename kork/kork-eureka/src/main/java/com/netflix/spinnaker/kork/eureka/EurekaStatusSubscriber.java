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
package com.netflix.spinnaker.kork.eureka;

import static com.netflix.spinnaker.kork.eureka.InstanceStatusUtil.fromEureka;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.InvalidSubscriberException;
import com.netflix.eventbus.spi.Subscribe;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusChangeEvent;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusPublisher;
import com.netflix.spinnaker.kork.discovery.InstanceStatus;
import com.netflix.spinnaker.kork.discovery.RemoteStatusChangedEvent;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.util.Objects;
import javax.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;

public class EurekaStatusSubscriber implements DiscoveryStatusPublisher {

  private final ApplicationEventPublisher publisher;
  private final EventBus eventBus;

  public EurekaStatusSubscriber(
      ApplicationEventPublisher publisher, EventBus eventBus, DiscoveryClient discoveryClient) {
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    publish(
        new DiscoveryStatusChangeEvent(
            InstanceStatus.UNKNOWN, fromEureka(discoveryClient.getInstanceRemoteStatus())));
    try {
      eventBus.registerSubscriber(this);
    } catch (InvalidSubscriberException ise) {
      throw new SystemException(ise);
    }
  }

  @PreDestroy
  public void shutdown() {
    eventBus.unregisterSubscriber(this);
  }

  private void publish(DiscoveryStatusChangeEvent event) {
    publisher.publishEvent(new RemoteStatusChangedEvent(event));
  }

  @Subscribe(name = "eurekaStatusSubscriber")
  public void onStatusChange(StatusChangeEvent event) {
    publish(
        new DiscoveryStatusChangeEvent(
            fromEureka(event.getPreviousStatus()), fromEureka(event.getStatus())));
  }
}
