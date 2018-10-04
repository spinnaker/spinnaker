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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/instances")
class InstanceController {

  @Autowired
  List<InstanceProvider> instanceProviders

  @Autowired
  MessageSource messageSource

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account}/{region}/{id:.+}", method = RequestMethod.GET)
  Instance getInstance(@PathVariable String account,
                       @PathVariable String region,
                       @PathVariable String id) {
    Collection<Instance> instanceMatches = instanceProviders.findResults {
      it.getInstance(account, region, id)
    }
    if (!instanceMatches) {
      throw new NotFoundException("Instance not found (id: ${id})")
    }
    instanceMatches.first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "{account}/{region}/{id}/console", method = RequestMethod.GET)
  Map getConsoleOutput(@RequestParam(value = "provider", required = false) String provider, // deprecated
                       @RequestParam(value = "cloudProvider", required = false) String cloudProvider,
                       @PathVariable String account,
                       @PathVariable String region,
                       @PathVariable String id) {
    String providerParam = cloudProvider ?: provider
    Collection outputs = instanceProviders.findResults {
      if (!providerParam || it.cloudProvider == providerParam) {
        return it.getConsoleOutput(account, region, id)
      }
      null
    }
    if (!outputs) {
      throw new NotFoundException("Instance not found (id: ${id})")
    }
    [ output: outputs.first() ]
  }
}
