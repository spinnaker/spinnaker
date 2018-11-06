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

import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * responsible for sending events to classes that implement an EchoEventListener
 */
@Slf4j
@SuppressWarnings({"CatchException"})
public class EventPropagator {
  private List<EchoEventListener> listeners = new ArrayList<>();
  private Scheduler scheduler = Schedulers.io();

  public void addListener(EchoEventListener listener) {
    listeners.add(listener);
    log.info("Added listener " + listener.getClass().getSimpleName());
  }

  public void processEvent(Event event) {
    Observable.from(listeners)
      .map(listener ->
        AuthenticatedRequest.propagate(() -> {
          listener.processEvent(event);
          return null;
        })
      )
      .observeOn(scheduler)
      .subscribe(callable -> {
          try {
            callable.call();
          } catch (Exception e) {
            log.error("failed processing event: {}", event.content,  e);
          }
        }
      );
  }
}
