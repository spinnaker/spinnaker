/*
 * Copyright 2016 Google, Inc.
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

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/subnets")
class SubnetController {

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @ApiOperation(value = "Retrieve a list of subnets for a given cloud provider", response = List.class)
  @RequestMapping(value = "/{cloudProvider}", method = RequestMethod.GET)
  List<Map> allByCloudProvider(@PathVariable String cloudProvider) {
    clouddriverServiceSelector.select().getSubnets(cloudProvider)
  }
}
