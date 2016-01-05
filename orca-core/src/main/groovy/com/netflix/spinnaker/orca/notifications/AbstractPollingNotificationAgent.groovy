/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.notifications

import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent

import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.pipeline.model.Execution
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import net.greghaines.jesque.Job
import net.greghaines.jesque.client.Client
import org.springframework.context.ApplicationListener
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.functions.Func1
import rx.schedulers.Schedulers

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP

@Slf4j
@CompileStatic
abstract class AbstractPollingNotificationAgent implements ApplicationListener<RemoteStatusChangedEvent> {

  protected final ObjectMapper objectMapper
  private final Client jesqueClient

  private Scheduler scheduler = Schedulers.io()
  private final AtomicReference<Subscription> subscription = new AtomicReference<>(null)
  private final Lock subscriptionLock = new ReentrantLock()

  AbstractPollingNotificationAgent(ObjectMapper objectMapper, Client jesqueClient) {
    this.objectMapper = objectMapper
    this.jesqueClient = jesqueClient
  }

  abstract long getPollingInterval()

  abstract String getNotificationType()

  protected Func1<Execution, Boolean> filter() {
    return new Func1<Execution, Boolean>() {
      @Override
      Boolean call(Execution map) {
        // default implementation does no filtering
        return true
      }
    }
  }

  protected abstract Observable<Execution> getEvents()

  // TODO: can we just use logical names rather than handler classes?
  abstract Class<? extends NotificationHandler> handlerType()

  void startPolling() {
    subscriptionLock.lockInterruptibly()
    try {
      if (subscription.get() == null) {
        subscription.set(Observable
          .timer(pollingInterval, TimeUnit.SECONDS, scheduler)
          .flatMap({ Long ignored -> getEvents() } as Func1<Long, Observable<Execution>>)
          .doOnError { Throwable err -> log.error("Error fetching executions", err) }
          .retry()
          .filter(filter())
          .map { Execution execution -> objectMapper.convertValue(execution, Map) }
          .flatMap(Observable.&from as Func1<Map, Observable<Map>>)
          .repeat()
          .subscribe { Map event -> notify(event) })
        log.info("polling for $notificationType started")
      } else {
        log.info("polling for $notificationType was already running")
      }
    } finally {
      subscriptionLock.unlock()
    }
  }

  @PreDestroy
  void stopPolling() {
    subscriptionLock.lockInterruptibly()
    try {
      def sub = subscription.getAndSet(null)
      sub?.unsubscribe()
      if (sub != null) {
        log.info("polling for $notificationType stopped")
      } else {
        log.info("polling for $notificationType was not running")
      }
    } finally {
      subscriptionLock.unlock()
    }
  }

  protected void notify(Map<String, ?> input) {
    jesqueClient.enqueue(
      notificationType,
      new Job(handlerType().name, input)
    )
  }

  @VisibleForTesting
  void setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler
  }

  @Override
  void onApplicationEvent(RemoteStatusChangedEvent event) {
    event.source.with {
      if (it.status == UP) {
        log.info("Instance is $it.status... starting polling for $notificationType events")
        startPolling()
      } else {
        log.warn("Instance is $it.status... stopping polling for $notificationType events")
        stopPolling()
      }
    }
  }
}
