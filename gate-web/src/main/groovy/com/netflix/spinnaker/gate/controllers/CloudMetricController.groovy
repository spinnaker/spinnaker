/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.CloudMetricService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/cloudMetrics")
@RestController
class CloudMetricController {

  @Autowired
  CloudMetricService cloudMetricService

  @RequestMapping(value = "/{cloudProvider}/{account}/{region}", method = RequestMethod.GET)
  List<Map> findAll(@PathVariable String cloudProvider,
                    @PathVariable String account,
                    @PathVariable String region,
                    @RequestParam Map<String, String> filters,
                    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {

    cloudMetricService.findAll(cloudProvider, account, region, filters, sourceApp)
  }

  @RequestMapping(value = "/{cloudProvider}/{account}/{region}/{metricName}/statistics", method = RequestMethod.GET)
  Map getStatistics(@PathVariable String cloudProvider,
                    @PathVariable String account,
                    @PathVariable String region,
                    @PathVariable String metricName,
                    @RequestParam(required = false) Long startTime,
                    @RequestParam(required = false) Long endTime,
                    @RequestParam Map<String, String> filters,
                    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {

    cloudMetricService.getStatistics(cloudProvider, account, region, metricName, startTime, endTime, filters, sourceApp)
  }
}
