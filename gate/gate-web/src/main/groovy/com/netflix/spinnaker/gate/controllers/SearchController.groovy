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
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

import jakarta.servlet.http.HttpServletRequest

@RestController
class SearchController {
  @Autowired
  SearchService searchService

  @Operation(summary = "Search infrastructure")
  @RequestMapping(value = "/search", method = RequestMethod.GET)
  List<Map> search(@RequestParam(value = "q", defaultValue = "", required = false) String query,
                   @RequestParam(value = "type") String type,
                   @RequestParam(value = "platform", required = false) String platform,
                   @RequestParam(value = "pageSize", defaultValue = "10000", required = false) int pageSize,
                   @RequestParam(value = "page", defaultValue = "1", required = false) int page,
                   @RequestParam(value = "allowShortQuery", defaultValue = "false", required = false) boolean allowShortQuery,
                   @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp,
                   HttpServletRequest httpServletRequest) {
    if (!allowShortQuery && query?.size() < 3) {
      // keyword searches must have a minimum of 3 characters
      return []
    }

    def filters = httpServletRequest.getParameterNames().findAll { String parameterName ->
      !["q", "type", "platform", "pageSize", "page", "allowShortQuery"].contains(parameterName)
    }.collectEntries { String parameterName ->
      [parameterName, httpServletRequest.getParameter(parameterName)]
    }
    searchService.search(query, type, platform, sourceApp, pageSize, page, filters)
  }
}
