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

package com.netflix.spinnaker.mort.web

import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/securityGroups")
@RestController
class SecurityGroupController {

  @Autowired
  List<SecurityGroupProvider> securityGroupProviders

  @RequestMapping(method = RequestMethod.GET)
  Set<SecurityGroup> list() {
    securityGroupProviders.collectMany {
      it.all
    } as Set
  }

  @RequestMapping(method = RequestMethod.GET, value = 'account/{account}/region/{region}')
  Set<SecurityGroup> getByAccountAndRegion(@PathVariable String account, @PathVariable String region) {
    securityGroupProviders.collectMany {
      it.getAllByAccountAndRegion(account, region)
    } as Set
  }

  @RequestMapping(method = RequestMethod.GET, value = 'region/{region}')
  Set<SecurityGroup> getByRegion(@PathVariable String region) {
    securityGroupProviders.collectMany {
      it.getAllByRegion(region)
    } as Set
  }

  @RequestMapping(method = RequestMethod.GET, value = 'account/{account}')
  Set<SecurityGroup> getByAccount(@PathVariable String account) {
    securityGroupProviders.collectMany {
      it.getAllByAccount(account)
    } as Set
  }

  @RequestMapping(method = RequestMethod.GET, value = 'get/{account}/{id}')
  SecurityGroup get(@PathVariable String account, @PathVariable String id) {
    SecurityGroup result = null
    securityGroupProviders.each {
      def sg = it.get(account, id)
      if (sg != null) {
        result = sg
      }
    }
    result
  }

}
