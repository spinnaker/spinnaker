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

import com.netflix.spinnaker.gate.services.SecurityGroupService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/securityGroups")
class SecurityGroupController {

  @Autowired
  SecurityGroupService securityGroupService

  @RequestMapping(method = RequestMethod.GET)
  Map all() {
    securityGroupService.all
  }

  @RequestMapping(value = "/{account}")
  Map allByAccount(
      @PathVariable String account,
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "region", required = false) String region) {
    securityGroupService.getForAccountAndProviderAndRegion(account, provider, region)
  }

  @RequestMapping(value = "/{account}/{name}")
  Map getSecurityGroup(
      @PathVariable String account,
      @PathVariable String name,
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "region", required = false) String region) {
    securityGroupService.getSecurityGroup(account, provider, name, region)
  }
}
