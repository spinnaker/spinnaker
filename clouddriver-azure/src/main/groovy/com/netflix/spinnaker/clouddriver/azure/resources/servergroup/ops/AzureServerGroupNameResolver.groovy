/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import groovy.transform.CompileStatic

@CompileStatic
class AzureServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String AZURE_PHASE = "AZURE_DEPLOY"

  private final String accountName
  private final String region
  private final AzureCredentials credentials

  AzureServerGroupNameResolver(String accountName,
                               String region,
                               AzureCredentials credentials) {
    this.accountName = accountName
    this.region = region
    this.credentials = credentials
  }

  @Override
  String getPhase() {
    return AZURE_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    String resourceGroupName = AzureUtilities.getResourceGroupName(AzureUtilities.getAppNameFromAzureResourceName(clusterName), region)

    def serverGroupsInRegion = credentials.computeClient.getServerGroupsAll(region, resourceGroupName).findAll {
      Names.parseName(it.name).cluster == clusterName
    }

    serverGroupsInRegion.collect {
      new AbstractServerGroupNameResolver.TakenSlot(
        serverGroupName: it.name,
        sequence       : Names.parseName(it.name).sequence,
        createdTime    : new Date(it.lastReadTime)
      )
    }
  }
}

