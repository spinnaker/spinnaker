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

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.mort.model.SecurityGroup
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/securityGroups")
@RestController
class SecurityGroupController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<SecurityGroupProvider> securityGroupProviders

  @RequestMapping(method = RequestMethod.GET)
  Map<String, Map<String, Set<SecurityGroup>>> list() {
    rx.Observable.from(accountCredentialsProvider.all).flatMap { acct ->
      rx.Observable.from(securityGroupProviders).flatMap { secGrpProv ->
        rx.Observable.from(secGrpProv.getAllByAccount(acct.name))
      }
    } filter {
      it != null
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.accountName)) {
        objs[obj.accountName] = [:]
      }
      if (!objs[obj.accountName].containsKey(obj.type)) {
        objs[obj.accountName][obj.type] = sortedTreeSet
      }
      objs[obj.accountName][obj.type] << obj.name
      objs
    }) toBlocking() first()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}")
  Map<String, Set<SecurityGroup>> listByAccount(@PathVariable String account) {
    rx.Observable.from(securityGroupProviders).flatMap { secGrpProv ->
      rx.Observable.from(secGrpProv.getAllByAccount(account))
    } filter {
      it != null
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.type)) {
        objs[obj.type] = sortedTreeSet
      }
      objs[obj.type] << obj.name
      objs
    }) toBlocking() first()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}", params = ['region'])
  Map<String, Set<SecurityGroup>> listByAccountAndRegion(@PathVariable String account,
                                                          @RequestParam("region") String region) {
    rx.Observable.from(securityGroupProviders).flatMap { secGrpProv ->
      rx.Observable.from(secGrpProv.getAllByAccountAndRegion(account, region))
    } filter {
      it != null
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.type)) {
        objs[obj.type] = sortedTreeSet
      }
      objs[obj.type] << obj.name
      objs
    }) toBlocking() first()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{type}")
  Set<SecurityGroup> listByAccountAndType(@PathVariable String account, @PathVariable String type) {
    rx.Observable.from(securityGroupProviders).filter { secGrpProv ->
      secGrpProv.type == type
    } flatMap {
      rx.Observable.from(it.getAllByAccount(account))
    } reduce(sortedTreeSet, { Set objs, SecurityGroup obj ->
      objs << obj.name
      objs
    }) toBlocking() first()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{type}", params = ['region'])
  Set<SecurityGroup> listByAccountAndTypeAndRegion(@PathVariable String account, @PathVariable String type,
                                           @RequestParam("region") String region) {
    rx.Observable.from(securityGroupProviders).filter { secGrpProv ->
      secGrpProv.type == type
    } flatMap {
      rx.Observable.from(it.getAllByAccountAndRegion(account, region))
    } reduce(sortedTreeSet, { Set objs, SecurityGroup obj ->
      objs << obj.name
      objs
    }) toBlocking() first()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{type}/{securityGroupName}")
  SecurityGroup listByAccountAndType(@PathVariable String account, @PathVariable String type,
                                           @PathVariable String securityGroupName) {
    securityGroupProviders.find { secGrpProv ->
      secGrpProv.type == type
    }.get(account, securityGroupName)
  }

  private static Set<SecurityGroup> getSortedTreeSet() {
    new TreeSet<>({ SecurityGroup a, SecurityGroup b -> a.name <=> b.name } as Comparator)
  }
}
