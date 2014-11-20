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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.EventService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.*

@CompileStatic
@RestController
class EventController {

  @Autowired
  EventService eventService

  @RequestMapping(value = "/events", method = RequestMethod.GET)
  HttpEntity<List<Map>> all(@RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
                            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
    def events = eventService.getAll(offset, size)
    def headers = new HttpHeaders()
    headers.add("X-Result-Total", Integer.valueOf(events.total as String).toString())
    headers.add("X-Result-Offset", offset.toString())
    headers.add("X-Result-Size", Integer.valueOf(events.paginationSize as String).toString())
    new HttpEntity(events.hits, headers)
  }

  @RequestMapping(value = "/applications/{app}/events", method = RequestMethod.GET)
  List getForApp(@PathVariable("app") String app) {
    eventService.getForApplication(app)?.hits as List<Map>
  }
}
