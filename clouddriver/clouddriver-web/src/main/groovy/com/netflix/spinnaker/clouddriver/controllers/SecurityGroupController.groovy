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

import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/securityGroups")
@RestController
@Slf4j
class SecurityGroupController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<SecurityGroupProvider> securityGroupProviders

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(method = RequestMethod.GET)
  Map<String, Map<String, Map<String, Set<SecurityGroupSummary>>>> list() {
    rx.Observable.from(securityGroupProviders).flatMap { secGrpProv ->
      rx.Observable.from(secGrpProv.getAll(false))
    } filter {
      it != null
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.accountName)) {
        objs[obj.accountName] = [:]
      }
      if (!objs[obj.accountName].containsKey(obj.cloudProvider)) {
        objs[obj.accountName][obj.cloudProvider] = [:]
      }
      if (!objs[obj.accountName][obj.cloudProvider].containsKey(obj.region)) {
        objs[obj.accountName][obj.cloudProvider][obj.region] = sortedTreeSet
      }
      objs[obj.accountName][obj.cloudProvider][obj.region] << obj.summary
      objs
    }) doOnError {
      log.error("Unable to list security groups", it)
    } toBlocking() first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(method = RequestMethod.GET, value = "/{account}")
  Map<String, Map<String, Set<SecurityGroupSummary>>> listByAccount(@PathVariable String account) {
    rx.Observable.from(securityGroupProviders).flatMap { secGrpProv ->
      rx.Observable.from(secGrpProv.getAllByAccount(false, account))
    } filter {
      it != null
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.cloudProvider)) {
        objs[obj.cloudProvider] = [:]
      }
      if (!objs[obj.cloudProvider].containsKey(obj.region)) {
        objs[obj.cloudProvider][obj.region] = sortedTreeSet
      }
      objs[obj.cloudProvider][obj.region] << obj.summary
      objs
    }) toBlocking() first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(method = RequestMethod.GET, value = "/{account}", params = ['region'])
  Map<String, Set<SecurityGroupSummary>> listByAccountAndRegion(@PathVariable String account,
                                                                @RequestParam("region") String region) {
    rx.Observable.from(securityGroupProviders).flatMap { secGrpProv ->
      rx.Observable.from(secGrpProv.getAllByAccountAndRegion(false, account, region))
    } filter {
      it != null
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.cloudProvider)) {
        objs[obj.cloudProvider] = sortedTreeSet
      }
      objs[obj.cloudProvider] << obj.summary
      objs
    }) toBlocking() first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{cloudProvider}")
  Map<String, Set<SecurityGroupSummary>> listByAccountAndCloudProvider(@PathVariable String account,
                                                              @PathVariable String cloudProvider) {
    rx.Observable.from(securityGroupProviders).filter { secGrpProv ->
      secGrpProv.cloudProvider == cloudProvider
    } flatMap {
      rx.Observable.from(it.getAllByAccount(false, account))
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.region)) {
        objs[obj.region] = sortedTreeSet
      }
      objs[obj.region] << obj.summary
      objs
    }) toBlocking() first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{cloudProvider}", params = ['region'])
  Set<SecurityGroupSummary> listByAccountAndCloudProviderAndRegion(@PathVariable String account,
                                                          @PathVariable String cloudProvider,
                                                          @RequestParam("region") String region) {
    rx.Observable.from(securityGroupProviders).filter { secGrpProv ->
      secGrpProv.cloudProvider == cloudProvider
    } flatMap {
      rx.Observable.from(it.getAllByAccountAndRegion(false, account, region))
    } reduce(sortedTreeSet, { Set objs, SecurityGroup obj ->
      objs << obj.summary
      objs
    }) toBlocking() first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{cloudProvider}/{securityGroupName:.+}")
  Map<String, Set<SecurityGroupSummary>> listByAccountAndCloudProviderAndName(@PathVariable String account,
                                                                     @PathVariable String cloudProvider,
                                                                     @PathVariable String securityGroupName) {
    rx.Observable.from(securityGroupProviders).filter { secGrpProv ->
      secGrpProv.cloudProvider == cloudProvider
    } flatMap {
      rx.Observable.from(it.getAllByAccountAndName(false, account, securityGroupName))
    } reduce([:], { Map objs, SecurityGroup obj ->
      if (!objs.containsKey(obj.region)) {
        objs[obj.region] = sortedTreeSet
      }
      objs[obj.region] << obj.summary
      objs
    }) toBlocking() first()
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(method = RequestMethod.GET, value = "/{account}/{cloudProvider}/{region}/{securityGroupNameOrId:.+}")
  SecurityGroup get(
    @PathVariable String account,
    @PathVariable String cloudProvider,
    @PathVariable String region,
    @PathVariable String securityGroupNameOrId,
    @RequestParam(value = "vpcId", required = false, defaultValue = "*") String vpcId,
    @RequestParam(value = "getById", required = false, defaultValue = "false") boolean getById
  ) {
    def securityGroup = securityGroupProviders.findResults { secGrpProv ->
      if (secGrpProv.cloudProvider == cloudProvider) {
        getById
          ? secGrpProv.getById(account, region, securityGroupNameOrId, vpcId)
          : secGrpProv.get(account, region, securityGroupNameOrId, vpcId)
      } else {
        null
      }
    }

    if (securityGroup.size() != 1) {
      throw new NotFoundException("Security group '${securityGroupNameOrId}' does not exist")
    }

    return securityGroup.first()
  }

  private static Set<SecurityGroupSummary> getSortedTreeSet() {
    new TreeSet<>({ SecurityGroupSummary a, SecurityGroupSummary b ->
      a.name.toLowerCase() <=> b.name.toLowerCase() ?: a.id <=> b.id
    } as Comparator)
  }
}
