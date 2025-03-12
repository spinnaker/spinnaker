/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.GetAppNamespaceResponse

import java.time.Instant

class DcosServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String DCOS_PHASE = "DCOS_DEPLOY"

  private final DCOS dcosClient
  private String region

  DcosServerGroupNameResolver(DCOS dcosClient, String account, String group) {
    this.dcosClient = dcosClient
    this.region = group ? "/${account}/${group}" : "/${account}"
  }

  @Override
  String getPhase() {
    return DCOS_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    Optional<GetAppNamespaceResponse> appNamespaceResponse = dcosClient.maybeApps(region)
    List<App> apps = appNamespaceResponse != null && appNamespaceResponse.isPresent() ? appNamespaceResponse.get().apps : []

    if (!apps) {
      return []
    }

    def filteredApps = apps.findAll {
      def appId = DcosSpinnakerAppId.parseVerbose(it.id)
      appId.isPresent() && appId.get().namespace == region && appId.get().serverGroupName.cluster == Names.parseName(clusterName).cluster
    }

    return filteredApps.collect { App app ->
      final def names = DcosSpinnakerAppId.parseVerbose(app.id).get().serverGroupName
      return new AbstractServerGroupNameResolver.TakenSlot(
        serverGroupName: names.group,
        sequence: names.sequence,
        createdTime: new Date(translateTime(app.versionInfo.lastConfigChangeAt))
      )
    }
  }

  static long translateTime(String time) {
    time ? Instant.parse(time).toEpochMilli() : 0
  }
}
