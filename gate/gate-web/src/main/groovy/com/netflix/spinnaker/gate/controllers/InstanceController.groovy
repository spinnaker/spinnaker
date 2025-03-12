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

import com.netflix.spinnaker.gate.services.InstanceService
import groovy.transform.CompileStatic
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RequestMapping("/instances")
@RestController
class InstanceController {
  @Autowired
  InstanceService instanceService

  @Operation(summary = "Retrieve an instance's details")
  @RequestMapping(value = "/{account}/{region}/{instanceId:.+}", method = RequestMethod.GET)
  Map getInstanceDetails(@PathVariable(value = "account") String account,
                         @PathVariable(value = "region") String region,
                         @PathVariable(value = "instanceId") String instanceId,
                         @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    instanceService.getForAccountAndRegion(account, region, instanceId, sourceApp)
  }

  @Operation(summary = "Retrieve an instance's console output")
  @RequestMapping(value = "/{account}/{region}/{instanceId}/console", method = RequestMethod.GET)
  Map getConsoleOutput(@PathVariable(value = "account") String account,
                       @PathVariable(value = "region") String region,
                       @PathVariable(value = "instanceId") String instanceId,
                       @RequestParam(value = "provider", required = false, defaultValue = "aws") String provider,
                       @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    instanceService.getConsoleOutput(account, region, instanceId, provider, sourceApp)
  }
}
