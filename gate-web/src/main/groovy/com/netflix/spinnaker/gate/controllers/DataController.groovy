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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.DataService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping(value = "/v1/data")
class DataController {
  @Autowired
  DataService dataService

  @RequestMapping(value = "/static/{id}", method = RequestMethod.GET)
  void getStaticData(@PathVariable("id") String id,
                     @RequestParam Map<String, String> filters,
                     @RequestParam(name = "expectedContentType", required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String expectedContentType,
                     HttpServletResponse httpServletResponse) {
    httpServletResponse.setContentType(expectedContentType)
    dataService.getStaticData(id, filters, httpServletResponse.getOutputStream())
  }

  @RequestMapping(value = "/adhoc/{groupId}/{bucketId}/**")
  void getAdhocData(@PathVariable("groupId") String groupId,
                    @PathVariable("bucketId") String bucketId,
                    @RequestParam(name = "expectedContentType", required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String expectedContentType,
                    HttpServletRequest httpServletRequest,
                    HttpServletResponse httpServletResponse) {
    String pattern = (String) httpServletRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
    String objectId = new AntPathMatcher().extractPathWithinPattern(pattern, httpServletRequest.getServletPath())

    httpServletResponse.setContentType(expectedContentType)
    dataService.getAdhocData(groupId, bucketId, objectId, httpServletResponse.getOutputStream())
  }
}
