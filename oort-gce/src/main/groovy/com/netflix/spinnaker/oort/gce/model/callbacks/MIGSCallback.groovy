/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.replicapool.model.InstanceGroupManagerList
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.gce.model.GoogleApplication
import com.netflix.spinnaker.oort.gce.model.GoogleCluster
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import com.netflix.spinnaker.oort.gce.model.ResourceViewsBuilder
import org.apache.log4j.Logger

class MIGSCallback<InstanceGroupManagerList> extends JsonBatchCallback<InstanceGroupManagerList> {
  protected static final Logger log = Logger.getLogger(this)

  private static final String GOOGLE_SERVER_GROUP_TYPE = "gce"

  private HashMap<String, GoogleApplication> tempAppMap
  private String region
  private String localZoneName
  private String accountName
  private String project
  private Compute compute
  private GoogleCredential.Builder credentialBuilder
  private Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap
  private BatchRequest resourceViewsBatch

  public MIGSCallback(HashMap<String, GoogleApplication> tempAppMap,
                      String region,
                      String localZoneName,
                      String accountName,
                      String project,
                      Compute compute,
                      GoogleCredential.Builder credentialBuilder,
                      Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap,
                      BatchRequest resourceViewsBatch) {
    this.tempAppMap = tempAppMap
    this.region = region
    this.localZoneName = localZoneName
    this.accountName = accountName
    this.project = project
    this.compute = compute
    this.credentialBuilder = credentialBuilder
    this.instanceNameToGoogleServerGroupMap = instanceNameToGoogleServerGroupMap
    this.resourceViewsBatch = resourceViewsBatch
  }

  @Override
  void onSuccess(InstanceGroupManagerList instanceGroupManagerList, HttpHeaders responseHeaders) throws IOException {
    for (def instanceGroupManager : instanceGroupManagerList.getItems()) {
      def names = Names.parseName(instanceGroupManager.name)
      def appName = names.app.toLowerCase()

      if (appName) {
        def cluster = Utils.retrieveOrCreatePathToCluster(tempAppMap, accountName, appName, names.cluster)

        // instanceGroupManager.name == names.group
        def googleServerGroup = new GoogleServerGroup(instanceGroupManager.name, GOOGLE_SERVER_GROUP_TYPE, region)
        googleServerGroup.zones << localZoneName
        googleServerGroup.setProperty(
          "launchConfig", [createdTime: Utils.getTimeFromTimestamp(instanceGroupManager.creationTimestamp)])

        def resourceViews = new ResourceViewsBuilder().buildResourceViews(credentialBuilder, Utils.APPLICATION_NAME)
        def resourceViewsCallback = new ResourceViewsCallback(localZoneName,
                                                              googleServerGroup,
                                                              project,
                                                              compute,
                                                              instanceNameToGoogleServerGroupMap)
        resourceViews.zoneViews().listResources(project,
                                                localZoneName,
                                                instanceGroupManager.name).queue(resourceViewsBatch,
                                                                                 resourceViewsCallback)

        def localInstanceTemplateName = Utils.getLocalName(instanceGroupManager.instanceTemplate)
        def instanceTemplatesCallback = new InstanceTemplatesCallback(googleServerGroup, cluster)
        compute.instanceTemplates().get(project,
                                        localInstanceTemplateName).queue(resourceViewsBatch,
                                                                         instanceTemplatesCallback)

        // The isDisabled property of a server group is set based on whether there are associated target pools.
        def targetPoolLoadBalancerNames =
          Utils.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(instanceGroupManager.getTargetPools())

        googleServerGroup.setDisabled(targetPoolLoadBalancerNames.empty)

        // oort.aws puts a com.amazonaws.services.autoscaling.model.AutoScalingGroup here. More importantly, deck expects it.
        googleServerGroup.setProperty("asg", [minSize          : instanceGroupManager.targetSize,
                                              maxSize          : instanceGroupManager.targetSize,
                                              desiredCapacity  : instanceGroupManager.targetSize])

        cluster.serverGroups << googleServerGroup
      }
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
