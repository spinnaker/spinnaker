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

package com.netflix.spinnaker.gate.controllers

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest
import com.netflix.spinnaker.gate.services.CanaryService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@CompileStatic
@ConditionalOnProperty('services.mine.enabled')
class CanaryController {

  @Autowired
  CanaryService canaryService

  @RequestMapping(value = "/canaries/{id:.+}/generateCanaryResult", method = RequestMethod.POST)
  @ResponseStatus(value = HttpStatus.CREATED)
  void generateCanaryResult(@PathVariable("id") String id,
                            @RequestBody GenerateResultCommand generateResultCommand) {
    canaryService.generateCanaryResult(id, generateResultCommand.duration, generateResultCommand.durationUnit)
  }

  @RequestMapping(value = "/canaryDeployments/{canaryDeploymentId}/canaryAnalysisHistory", method = RequestMethod.GET)
  List<Map> showCanaryAnalysisHistory(@PathVariable String canaryDeploymentId) {
    canaryService.getCanaryAnalysisHistory(canaryDeploymentId)
  }

  @RequestMapping(value = "/canaries/{id}", method = RequestMethod.GET)
  Map showCanary(@PathVariable String id) {
    canaryService.showCanary(id)
  }

  @RequestMapping(value = "/canaries/{id:.+}/end", method = RequestMethod.PUT)
  Map endCanary(@PathVariable String id, @RequestBody OverrideResultCommand command) {
    canaryService.endCanary(id, command.result, command.reason)
  }

  @RequestMapping(value = "/canaryConfig/names", method = RequestMethod.GET)
  List<String> getCanaryConfigNames(@RequestParam String application) {
    canaryService.getCanaryConfigNames(application);
  }

  @RequestMapping(value = "/canaryConfigs/{application}", method = RequestMethod.GET)
  List<Map> canaryConfigsForApplication(@PathVariable String application) {
    canaryService.canaryConfigsForApplication(application)
  }

  static class GenerateResultCommand {
    int duration
    String durationUnit
  }

  static class OverrideResultCommand {
    String reason
    String result
  }

  @ExceptionHandler(UpstreamBadRequest.class)
  Map<String, String> upstreamBadRequestHandler(
    HttpServletRequest request,
    HttpServletResponse response,
    UpstreamBadRequest error) {

    response.setStatus(error.getStatus())

    [
      url        : request.requestURI,
      message    : error.message,
      upstreamUrl: error.url
    ]
  }
}
