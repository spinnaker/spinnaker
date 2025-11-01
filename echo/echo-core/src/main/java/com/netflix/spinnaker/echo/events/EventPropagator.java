/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.events;

import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.api.events.NotificationAgent;
import com.netflix.spinnaker.echo.notification.ExtensionNotificationAgent;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

/** responsible for sending events to classes that implement an EchoEventListener */
@Slf4j
@SuppressWarnings({"CatchException"})
public class EventPropagator {
  private final Scheduler scheduler;
  private final ObjectProvider<List<EventListener>> eventListenerProvider;
  private final List<EventListener> notificationAgents;

  public EventPropagator(
      ObjectProvider<List<EventListener>> eventListenerProvider,
      List<NotificationAgent> notificationAgents) {
    this(eventListenerProvider, notificationAgents, Schedulers.io());
  }

  public EventPropagator(
      ObjectProvider<List<EventListener>> eventListenerProvider,
      List<NotificationAgent> notificationAgents,
      Scheduler scheduler) {
    this.eventListenerProvider = eventListenerProvider;
    this.notificationAgents =
        Optional.ofNullable(notificationAgents).orElseGet(ArrayList::new).stream()
            .map(ExtensionNotificationAgent::new)
            .collect(Collectors.toList());
    this.scheduler = scheduler;
  }

  private Set<EventListener> eventListeners() {
    Set<EventListener> eventListeners = new HashSet<>();
    eventListeners.addAll(eventListenerProvider.getIfAvailable(ArrayList::new));
    eventListeners.addAll(notificationAgents);
    return eventListeners;
  }

  public void processEvent(Event event) {
    Observable.fromIterable(eventListeners())
        .map(
            listener ->
                AuthenticatedRequest.propagate(
                    () -> {
                      listener.processEvent(event);
                      return null;
                    }))
        .observeOn(scheduler)
        .subscribe(
            callable -> {
              try {
                callable.call();
              } catch (Exception e) {
                log.error("failed processing event: {}", event, e);
              }
            });
  }
}
