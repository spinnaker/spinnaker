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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.BakeService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/bakery")
@RestController
public class BakeController {

  @Autowired private BakeService bakeService;

  @Operation(summary = "Retrieve a list of available bakery base images, grouped by cloud provider")
  @RequestMapping(value = "/options", method = RequestMethod.GET)
  public Object bakeOptions() {
    return bakeService.bakeOptions();
  }

  @Operation(summary = "Retrieve a list of available bakery base images for a given cloud provider")
  @RequestMapping(value = "/options/{cloudProvider}", method = RequestMethod.GET)
  public Object bakeOptions(@PathVariable("cloudProvider") String cloudProvider) {
    return bakeService.bakeOptions(cloudProvider);
  }

  @Operation(summary = "Retrieve the logs for a given bake")
  @RequestMapping(value = "/logs/{region}/{statusId}", method = RequestMethod.GET)
  public String lookupLogs(
      @PathVariable("region") String region, @PathVariable("statusId") String statusId) {
    return bakeService.lookupLogs(region, statusId);
  }
}
