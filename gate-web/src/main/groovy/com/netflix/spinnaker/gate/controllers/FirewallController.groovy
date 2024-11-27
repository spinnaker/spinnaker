/*
 * Copyright 2018 Google, Inc.
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

import com.netflix.spinnaker.gate.services.SecurityGroupService
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/firewalls")
class FirewallController {

  @Autowired
  SecurityGroupService securityGroupService

  @Operation(summary = "Retrieve a list of firewalls, grouped by account, cloud provider, and region")
  @RequestMapping(method = RequestMethod.GET)
  Map all(@RequestParam(value = "id", required = false) String id,
          @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    if (id) {
      def result = securityGroupService.getById(id, sourceApp)
      if (result) {
        result
      } else {
        throw new NotFoundException("No firewall found (id: ${id})")
      }
    } else {
      securityGroupService.getAll(sourceApp)
    }
  }

  @Operation(summary = "Retrieve a list of firewalls for a given account, grouped by region")
  @RequestMapping(value = "/{account}", method = RequestMethod.GET)
  Map allByAccount(
    @PathVariable String account,
    @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    securityGroupService.getForAccountAndProvider(account, provider, sourceApp)
  }

  @Operation(summary = "Retrieve a list of firewalls for a given account and region")
  @RequestMapping(value = "/{account}/{region}", method = RequestMethod.GET)
  List allByAccountAndRegion(
    @PathVariable String account,
    @PathVariable String region,
    @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
    @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    securityGroupService.getForAccountAndProviderAndRegion(account, provider, region, sourceApp)
  }

  @Operation(summary = "Retrieve a firewall's details")
  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  Map getSecurityGroup(
      @PathVariable String account,
      @PathVariable String region,
      @PathVariable String name,
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "vpcId", required = false) String vpcId,
      @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    securityGroupService.getSecurityGroup(account, provider, name, region, sourceApp, vpcId)
  }
}
