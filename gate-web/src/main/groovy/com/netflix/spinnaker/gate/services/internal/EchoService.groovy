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

package com.netflix.spinnaker.gate.services.internal

import com.fasterxml.jackson.annotation.JsonProperty
import retrofit.client.Response
import retrofit.http.*

interface EchoService {

  @Headers("Accept: application/json")
  @GET("/search/events/0")
  Map getAllEvents(@Query("from") int offset,
                   @Query("size") int size,
                   @Query("full") boolean full)

  @Headers("Accept: application/json")
  @GET("/search/events/0")
  Map getEvents(@Query("application") String application,
                @Query("from") int offset,
                @Query("size") int size,
                @Query("full") boolean full)

  @POST("/")
  Response postEvent(@Body EventBuilder.Event event)

  static class EventBuilder {

    private Event event = new Event()

    static EventBuilder builder() {
      new EventBuilder()
    }

    EventBuilder withType(String type) {
      event.details.type = type
      this
    }

    EventBuilder withSource(String source) {
      event.details.source = source
      this
    }

    EventBuilder withContent(Map content) {
      event.contentMap = content
      this
    }

    Event build() {
      event
    }

    private static class Event {
      @JsonProperty("content")
      Map contentMap = [:]
      Map details = [:]
    }
  }
}
