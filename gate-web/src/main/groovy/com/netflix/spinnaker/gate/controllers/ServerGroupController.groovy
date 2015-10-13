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

import com.netflix.spinnaker.gate.services.ServerGroupService
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@CompileStatic
@RestController
class ServerGroupController {
  @Autowired
  ServerGroupService serverGroupService

  @RequestMapping(value = "/applications/{applicationName}/serverGroups", method = RequestMethod.GET)
  List getServerGroups(@PathVariable String applicationName, @RequestParam(required = false, value = 'expand', defaultValue = 'false') String expand) {
    serverGroupService.getForApplication(applicationName, expand)
  }

  @RequestMapping(value = "/applications/{applicationName}/serverGroups/{account}/{region}/{serverGroupName:.+}", method = RequestMethod.GET)
  Map getServerGroupDetails(@PathVariable String applicationName,
                            @PathVariable String account,
                            @PathVariable String region,
                            @PathVariable String serverGroupName) {
    def serverGroupDetails = serverGroupService.getForApplicationAndAccountAndRegion(applicationName, account, region, serverGroupName)
    if (!serverGroupDetails) {
      throw new ServerGroupNotFoundException()
    }

    return serverGroupDetails
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class ServerGroupNotFoundException extends RuntimeException {}
}
