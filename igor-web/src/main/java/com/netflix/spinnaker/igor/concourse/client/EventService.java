/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.igor.concourse.client.model.Event;
import com.netflix.spinnaker.igor.concourse.client.model.Token;
import java.io.IOException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;

@Slf4j
public class EventService {
  private final String host;
  private final OkHttpClient okHttpClient;
  private final ObjectMapper mapper;

  public EventService(String host, Supplier<Token> refreshToken, ObjectMapper mapper) {
    this.host = host;
    this.okHttpClient = OkHttpClientBuilder.retryingClient3(refreshToken);
    this.mapper = mapper;
  }

  public Flux<Event> resourceEvents(String buildId) {
    Request request =
        new Request.Builder().url(host + "/api/v1/builds/" + buildId + "/events").build();

    FluxProcessor<Event, Event> processor = UnicastProcessor.create();
    FluxSink<Event> sink = processor.sink();

    EventSource eventSource =
        EventSources.createFactory(okHttpClient)
            .newEventSource(
                request,
                new EventSourceListener() {
                  @Override
                  public void onEvent(
                      EventSource eventSource,
                      @Nullable String id,
                      @Nullable String type,
                      String data) {
                    if (data.isEmpty()) return;

                    try {
                      Event ev = mapper.readValue(data, Event.class);
                      Event.Origin origin = ev.getData().getOrigin();
                      // we don't care about task output
                      if (origin != null && !"stdout".equals(origin.getSource())) {
                        sink.next(ev);
                      }
                    } catch (IOException e) {
                      log.warn("Unable to read event", e);
                    }
                  }

                  @Override
                  public void onFailure(
                      EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                    if (!processor.isDisposed()) {
                      log.warn("Unable to connect to event stream", t);
                      sink.complete();
                    }
                    if (response != null) {
                      response.close();
                    }
                  }

                  @Override
                  public void onClosed(EventSource eventSource) {
                    sink.complete();
                  }
                });

    return processor.doOnCancel(eventSource::cancel);
  }
}
