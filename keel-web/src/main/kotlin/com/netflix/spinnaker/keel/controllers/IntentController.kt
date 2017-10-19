/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.controllers

import com.netflix.spinnaker.keel.IntentStatus
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import javax.ws.rs.QueryParam

@RestController
@RequestMapping("/intents")
class IntentController {

  @RequestMapping(method = arrayOf(RequestMethod.PUT))
  fun upsertIntent(@RequestBody intent: Map<String, Any>) {}

  @RequestMapping(method = arrayOf(RequestMethod.GET))
  fun getIntents(@QueryParam("statuses") statuses: List<IntentStatus>) {}
}
