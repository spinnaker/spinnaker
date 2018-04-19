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
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class AmazonInfrastructureController {

  @Autowired
  InfrastructureService infrastructureService

  @ApiOperation(value = "Get instance types", response = HashMap.class, responseContainer = "List")
  @RequestMapping(value = "/instanceTypes", method = RequestMethod.GET)
  List<Map> instanceTypes() {
    infrastructureService.instanceTypes
  }

  @ApiOperation(value = "Get key pairs", response = HashMap.class, responseContainer = "List")
  @RequestMapping(value = "/keyPairs", method = RequestMethod.GET)
  List<Map> keyPairs() {
    infrastructureService.keyPairs
  }

  @ApiOperation(value = "Get subnets", response = HashMap.class, responseContainer = "List")
  @RequestMapping(value = "/subnets", method = RequestMethod.GET)
  List<Map> subnets() {
    infrastructureService.subnets
  }

  @Deprecated
  @ApiOperation(value = "Get VPCs", response = HashMap.class, responseContainer = "List")
  @RequestMapping(value = "/vpcs", method = RequestMethod.GET)
  List<Map> vpcs() {
    infrastructureService.vpcs
  }
}
