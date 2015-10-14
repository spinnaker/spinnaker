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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.EchoService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class EventService {
  private static final String GROUP = "events"

  @Autowired(required = false)
  EchoService echoService

  Map getAll(int offset, int size) {
    if (echoService == null) {
      return [:]
    }
    HystrixFactory.newMapCommand(GROUP, "getAllEvents") {
      echoService.getAllEvents(offset, size, true)
    } execute()
  }

  Map getForApplication(String app) {
    if (echoService == null) {
      return [:]
    }
    HystrixFactory.newMapCommand(GROUP, "getEventsForApplication") {
      echoService.getEvents(app, 0, Integer.MAX_VALUE, true)
    } execute()
  }
}
