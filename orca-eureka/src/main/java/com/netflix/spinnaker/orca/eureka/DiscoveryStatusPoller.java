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

import java.util.Arrays;
import java.util.List;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import rx.Observable;
import rx.Scheduler;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UNKNOWN;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DiscoveryStatusPoller implements ApplicationListener<ContextRefreshedEvent> {

  private final DiscoveryClient discoveryClient;
  private final long pollingIntervalSeconds;
  private final Scheduler scheduler;
  private final ApplicationContext applicationContext;
  private final Logger log = LoggerFactory.getLogger(DiscoveryStatusPoller.class);

  public DiscoveryStatusPoller(DiscoveryClient discoveryClient,
                               @Qualifier("discoveryPollingFrequencySeconds") long pollingIntervalSeconds,
                               @Qualifier("discoveryPollingScheduler") Scheduler scheduler,
                               ApplicationContext applicationContext) {
    this.discoveryClient = discoveryClient;
    this.pollingIntervalSeconds = pollingIntervalSeconds;
    this.scheduler = scheduler;
    this.applicationContext = applicationContext;
  }

  @Override public void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("Starting polling for discovery status");
    Observable.interval(pollingIntervalSeconds, SECONDS, scheduler)
              .map(this::pollDiscovery)
              .doOnError(e -> log.warn("Error polling Eureka", e))
              .retry()
              .mergeWith(Observable.just(UNKNOWN))
              .distinctUntilChanged()
              .buffer(2, 1)
              .map(this::createEvent)
              .subscribe(this::publishEvent);
  }

  private void publishEvent(StatusChangeEvent event) {
    log.info("Application status changed from %s to %s", event.getPreviousStatus(), event.getStatus());
    applicationContext.publishEvent(new EurekaStatusChangedEvent(event));
  }

  private StatusChangeEvent createEvent(List<InstanceStatus> statusTuple) {
    assert statusTuple.size() == 2 : "Expected a tuple with 2 elements";
    return new StatusChangeEvent(statusTuple.get(0), statusTuple.get(1));
  }

  private StatusChangeEvent createEvent(InstanceStatus status) {
    return createEvent(Arrays.asList(UNKNOWN, status));
  }

  private InstanceStatus pollDiscovery(Long tick) {
    return discoveryClient.getInstanceRemoteStatus();
  }
}
