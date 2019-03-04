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
import com.google.api.services.compute.model.InstanceTemplate
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Namer
import org.springframework.util.StringUtils

class GCEServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String GCE_PHASE = "GCE_DEPLOY"

  private final String project
  private final String region
  private final GoogleNamedAccountCredentials credentials
  private final GoogleClusterProvider googleClusterProvider
  private final GoogleExecutorTraits executor
  private final Namer naming

  private SafeRetry safeRetry

  GCEServerGroupNameResolver(String project, String region, GoogleNamedAccountCredentials credentials, GoogleClusterProvider googleClusterProvider, SafeRetry safeRetry, GoogleExecutorTraits executor) {
    this.project = project
    this.region = region
    this.credentials = credentials
    this.googleClusterProvider = googleClusterProvider
    this.safeRetry = safeRetry
    this.executor = executor
    this.naming = NamerRegistry.lookup()
      .withProvider(GoogleCloudProvider.ID)
      .withAccount(credentials.name)
      .withResource(GoogleLabeledResource)
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
    def clusters = googleClusterProvider.getClusters(clusterName.split("-")[0], project)
    clusterName = clusters.find { cluster ->
      cluster.name == clusterName
    }?.moniker?.cluster ?: clusterName

    return findMatchingManagedInstanceGroups(managedInstanceGroups, clusterName)
  }

  private List<AbstractServerGroupNameResolver.TakenSlot> findMatchingManagedInstanceGroups(
      List<InstanceGroupManager> managedInstanceGroups, String clusterName) {
    if (!managedInstanceGroups) {
      return []
    }

    def instanceTemplates = GCEUtil.queryAllInstanceTemplates(credentials, executor)

    return managedInstanceGroups.findResults { managedInstanceGroup ->
      def instanceTemplateName = GCEUtil.getLocalName(managedInstanceGroup.getInstanceTemplate())
      def instanceTemplate = instanceTemplates.find { it.getName() == instanceTemplateName }
      def labeledInstanceTemplate = new GoogleInstanceTemplate(instanceTemplate.getProperties().getLabels())
      def moniker = naming.deriveMoniker(labeledInstanceTemplate)

      if (moniker.cluster == clusterName) {
        return new AbstractServerGroupNameResolver.TakenSlot(
          serverGroupName: managedInstanceGroup.name,
          sequence: moniker.sequence,
          createdTime: new Date(Utils.getTimeFromTimestamp(managedInstanceGroup.getCreationTimestamp()))
        )
      } else {
        return null
      }
    }
  }

  private class GoogleInstanceTemplate implements GoogleLabeledResource {
    Map<String, String> labels

    GoogleInstanceTemplate(Map<String, String> labels) {
      this.labels = labels
    }
  }
}
