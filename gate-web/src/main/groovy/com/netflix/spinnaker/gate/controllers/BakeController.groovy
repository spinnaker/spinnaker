/*
 * Copyright 2015 Google, Inc.
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

import com.netflix.spinnaker.gate.services.BakeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

@RequestMapping("/bakery")
@RestController
class BakeController {

  @Autowired
  BakeService bakeService

  @RequestMapping(value = "/options", method = RequestMethod.GET)
  def bakeOptions() {
    bakeService.bakeOptions()
  }

  @RequestMapping(value = "/options/{cloudProvider}", method = RequestMethod.GET)
  def bakeOptions(@PathVariable("cloudProvider") String cloudProvider) {
    bakeService.bakeOptions(cloudProvider)
  }

  @RequestMapping(value = "/logs/{region}/{statusId}", method = RequestMethod.GET)
  def lookupLogs(@PathVariable("region") String region,
                 @PathVariable("statusId") String statusId) {
    bakeService.lookupLogs(region, statusId)
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleBakeOptionsException(Exception e) {
    def errorMsg = e instanceof RetrofitError && e.getUrl().contains("/logs/") ? "logs.not.found" : "bake.options.not.found"

    return [error: errorMsg, status: HttpStatus.NOT_FOUND, message: e.message]
  }
}
