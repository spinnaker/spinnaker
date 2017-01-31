/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy

import com.google.api.services.compute.model.InstanceGroupManager
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver

class GCEServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String GCE_PHASE = "GCE_DEPLOY"

  private final String project
  private final String region
  private final GoogleNamedAccountCredentials credentials
  private final GoogleExecutorTraits executor

  private SafeRetry safeRetry

  GCEServerGroupNameResolver(String project, String region, GoogleNamedAccountCredentials credentials, SafeRetry safeRetry, GoogleExecutorTraits executor) {
    this.project = project
    this.region = region
    this.credentials = credentials
    this.safeRetry = safeRetry
    this.executor = executor
  }

  @Override
  String getPhase() {
    return GCE_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def managedInstanceGroups = GCEUtil.queryAllManagedInstanceGroups(project, region, credentials, task, phase, safeRetry, executor)

    return findMatchingManagedInstanceGroups(managedInstanceGroups, clusterName)
  }

  private List<AbstractServerGroupNameResolver.TakenSlot> findMatchingManagedInstanceGroups(
      List<InstanceGroupManager> managedInstanceGroups, String clusterName) {
    if (!managedInstanceGroups) {
      return []
    }

    return managedInstanceGroups.findResults { managedInstanceGroup ->
      def names = Names.parseName(managedInstanceGroup.name)

      if (names.cluster == clusterName) {
        return new AbstractServerGroupNameResolver.TakenSlot(
          serverGroupName: managedInstanceGroup.name,
          sequence       : names.sequence,
          createdTime    : new Date(Utils.getTimeFromTimestamp(managedInstanceGroup.creationTimestamp))
        )
      } else {
        return null
      }
    }
  }
}
