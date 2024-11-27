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

package com.netflix.spinnaker.gate.controllers.aws

import com.netflix.spinnaker.gate.services.aws.InfrastructureService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AmazonInfrastructureController {

  @Autowired
  InfrastructureService infrastructureService

  @Operation(summary = "Get instance types")
  @RequestMapping(value = "/instanceTypes", method = RequestMethod.GET)
  List<Map> instanceTypes() {
    infrastructureService.instanceTypes
  }

  @Operation(summary = "Get key pairs")
  @RequestMapping(value = "/keyPairs", method = RequestMethod.GET)
  List<Map> keyPairs() {
    infrastructureService.keyPairs
  }

  @Operation(summary = "Get subnets")
  @RequestMapping(value = "/subnets", method = RequestMethod.GET)
  List<Map> subnets() {
    infrastructureService.subnets
  }

  @Deprecated
  @Operation(summary = "Get VPCs")
  @RequestMapping(value = "/vpcs", method = RequestMethod.GET)
  List<Map> vpcs() {
    infrastructureService.vpcs
  }

  @Operation(summary = "Get functions")
  @RequestMapping(value = "/functions", method = RequestMethod.GET)
  List<Map> functions(@RequestParam(value = "functionName", required = false) String functionName,
                      @RequestParam(value = "region", required = false) String region,
                      @RequestParam(value = "account", required = false) String account) {
    infrastructureService.getFunctions(functionName, region, account)
  }

  @Operation(summary = "Get application functions")
  @RequestMapping(value = "/applications/{application}/functions", method = RequestMethod.GET)
  List<Map> applicationFunctions(@PathVariable String application) {
    infrastructureService.getApplicationFunctions(application)
  }
}
