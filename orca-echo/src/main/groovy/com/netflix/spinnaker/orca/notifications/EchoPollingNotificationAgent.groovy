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

package com.netflix.spinnaker.orca.notifications
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.echo.EchoEventPoller
import net.greghaines.jesque.client.Client
import rx.functions.Func1
/**
 * A notification agent for {@code EchoService} to poll for particular type of events
 */
abstract class EchoPollingNotificationAgent extends AbstractPollingNotificationAgent {

  protected final EchoEventPoller echoEventPoller

  EchoPollingNotificationAgent(ObjectMapper objectMapper,
                               EchoEventPoller echoEventPoller,
                               Client jesqueClient) {
    super(objectMapper, jesqueClient)
    this.echoEventPoller = echoEventPoller
  }

  @Override
  protected Func1<Long, List<Map>> getEvents() {
    return new Func1<Long, List<Map>>() {
      @Override
      List<Map> call(Long aLong) {
        def response = echoEventPoller.getEvents(notificationType)
        objectMapper.readValue(response.body.in().text, List)
      }
    }
  }
}
