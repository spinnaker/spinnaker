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

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import net.greghaines.jesque.Job
import net.greghaines.jesque.client.Client
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.schedulers.Schedulers

@Log4j
@CompileStatic
abstract class AbstractPollingNotificationAgent {

  protected final ObjectMapper objectMapper
  private final Client jesqueClient
  protected final EchoEventPoller echoEventPoller

  private Scheduler scheduler = Schedulers.io()
  private Subscription subscription

  AbstractPollingNotificationAgent(ObjectMapper objectMapper,
                                   EchoEventPoller echoEventPoller,
                                   Client jesqueClient) {
    this.objectMapper = objectMapper
    this.echoEventPoller = echoEventPoller
    this.jesqueClient = jesqueClient
  }

  abstract long getPollingInterval()

  abstract String getNotificationType()

  protected abstract List<Map> filterEvents(List<Map> input)

  @PostConstruct
  void init() {
    subscription = Observable.interval(pollingInterval, TimeUnit.SECONDS, scheduler).map {
      def response = echoEventPoller.getEvents(notificationType)
      objectMapper.readValue(response.body.in().text, List)
    } doOnError { Throwable err ->
      log.error "Error when fetching events", err
    } retry() map {
      filterEvents(it)
    } flatMap(Observable.&from) subscribe { Map event ->
      notify(event)
    }
  }

  @PreDestroy
  void shutdown() {
    subscription?.unsubscribe()
  }

  // TODO: can we just use logical names rather than handler classes?
  abstract Class<? extends NotificationHandler> handlerType()

  protected final void notify(Map<String, ?> input) {
    jesqueClient.enqueue(
      notificationType,
      new Job(handlerType().name, input)
    )
  }

  @VisibleForTesting
  void setScheduler(Scheduler scheduler) {
    this.scheduler = scheduler
  }
}
