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

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

@CompileStatic
@Component
class ClusterService {

  @Autowired
  ClouddriverServiceSelector clouddriverServiceSelector

  @Autowired
  ProviderLookupService providerLookupService

  Map getClusters(String app, String selectorKey) {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getClusters(app))
  }

  List<Map> getClustersForAccount(String app, String account, String selectorKey) {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getClustersForAccount(app, account))
  }

  Map getCluster(String app, String account, String clusterName, String selectorKey) {
    try {
      Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getCluster(app, account, clusterName))?.getAt(0) as Map
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        return [:]
      }
      throw e
    }
  }

  List<Map> getClusterServerGroups(String app, String account, String clusterName, String selectorKey) {
    getCluster(app, account, clusterName, selectorKey).serverGroups as List<Map>
  }

  List<Map> getScalingActivities(String app, String account, String clusterName, String serverGroupName, String provider, String region, String selectorKey) {
    Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getScalingActivities(app, account, clusterName, provider, serverGroupName, region))
  }

  Map getTargetServerGroup(String app, String account, String clusterName, String cloudProviderType, String scope, String target, Boolean onlyEnabled, Boolean validateOldest, String selectorKey) {
    try {
      return Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getTargetServerGroup(app, account, clusterName, cloudProviderType, scope, target, onlyEnabled, validateOldest))
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 404) {
        throw new ServerGroupNotFound("unable to find $target in $cloudProviderType/$account/$scope/$clusterName")
      }
      throw e
    }
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class ServerGroupNotFound extends SpinnakerException {}
}
