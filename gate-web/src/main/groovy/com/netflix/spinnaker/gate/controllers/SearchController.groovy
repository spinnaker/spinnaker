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

import com.netflix.spinnaker.gate.services.SearchService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
class SearchController {
  @Autowired
  SearchService searchService

  @RequestMapping(value = "/search", method = RequestMethod.GET)
  List<Map> search(@RequestParam(value = "q") String query,
                   @RequestParam(value = "type") String type,
                   @RequestParam(value = "platform", required = false) String platform,
                   @RequestParam(value = "pageSize", defaultValue = "10000", required = false) int pageSize,
                   @RequestParam(value = "page", defaultValue = "1", required = false) int page,
                   @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    searchService.search(query, type, platform, sourceApp, pageSize, page)
  }
}
