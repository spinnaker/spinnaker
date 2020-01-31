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

package com.netflix.spinnaker.gate.services

import com.netflix.hystrix.exception.HystrixBadRequestException
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import retrofit.RetrofitError

@CompileStatic
@Component
class ClusterService {
  private static final String GROUP = "clusters"

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @Autowired
  ProviderLookupService providerLookupService

  Map getClusters(String app, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getClustersForApplication") {
      clouddriverServiceSelector.select().getClusters(app)
    } execute()
  }

  List<Map> getClustersForAccount(String app, String account, String selectorKey) {
    HystrixFactory.newListCommand(GROUP, "getClustersForApplicationInAccount-${providerLookupService.providerForAccount(account)}") {
      clouddriverServiceSelector.select().getClustersForAccount(app, account)
    } execute()
  }

  Map getCluster(String app, String account, String clusterName, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getCluster-${providerLookupService.providerForAccount(account)}") {
      try {
        clouddriverServiceSelector.select().getCluster(app, account, clusterName)?.getAt(0) as Map
      } catch (RetrofitError e) {
        if (e.response?.status == 404) {
          return [:]
        } else {
          throw e
        }
      }
    } execute()
  }

  List<Map> getClusterServerGroups(String app, String account, String clusterName, String selectorKey) {
    getCluster(app, account, clusterName, selectorKey).serverGroups as List<Map>
  }

  List<Map> getScalingActivities(String app, String account, String clusterName, String serverGroupName, String provider, String region, String selectorKey) {
    HystrixFactory.newListCommand(GROUP, "getScalingActivitiesForCluster-${providerLookupService.providerForAccount(account)}") {
      clouddriverServiceSelector.select().getScalingActivities(app, account, clusterName, provider, serverGroupName, region)
    } execute()
  }

  Map getTargetServerGroup(String app, String account, String clusterName, String cloudProviderType, String scope, String target, Boolean onlyEnabled, Boolean validateOldest, String selectorKey) {
    HystrixFactory.newMapCommand(GROUP, "getTargetServerGroup-${providerLookupService.providerForAccount(account)}") {
      try {
        return clouddriverServiceSelector.select().getTargetServerGroup(app, account, clusterName, cloudProviderType, scope, target, onlyEnabled, validateOldest)
      } catch (RetrofitError re) {
        if (re.kind == RetrofitError.Kind.HTTP && re.response?.status == 404) {
          throw new ServerGroupNotFound("unable to find $target in $cloudProviderType/$account/$scope/$clusterName")
        }
        throw re
      }
    } execute()
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class ServerGroupNotFound extends HystrixBadRequestException {}
}
