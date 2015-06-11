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

package com.netflix.spinnaker.oort.controllers

import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/cache")
class CacheController {

  @Autowired
  List<OnDemandCacheUpdater> onDemandCacheUpdaters

  @RequestMapping(method = RequestMethod.POST, value = "/{type}")
  ResponseEntity handleOnDemand(@PathVariable String type, @RequestBody Map<String, ? extends Object> data) {
    for (updater in onDemandCacheUpdaters) {
      if (updater.handles(type)) {
        updater.handle(type, data)
      }
    }
    new ResponseEntity(HttpStatus.ACCEPTED)
  }
}
