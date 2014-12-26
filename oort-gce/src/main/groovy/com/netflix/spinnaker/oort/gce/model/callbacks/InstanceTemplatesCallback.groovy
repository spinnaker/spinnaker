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

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.InstanceTemplate
import com.netflix.spinnaker.oort.gce.model.GoogleCluster
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import groovy.json.JsonBuilder
import org.apache.log4j.Logger

class InstanceTemplatesCallback<InstanceTemplate> extends JsonBatchCallback<InstanceTemplate> {
  protected static final Logger log = Logger.getLogger(this)

  private GoogleServerGroup googleServerGroup
  private GoogleCluster googleCluster

  public InstanceTemplatesCallback(GoogleServerGroup googleServerGroup, GoogleCluster googleCluster) {
    this.googleServerGroup = googleServerGroup
    this.googleCluster = googleCluster
  }

  @Override
  void onSuccess(InstanceTemplate instanceTemplate, HttpHeaders responseHeaders) throws IOException {
    googleServerGroup.launchConfig.launchConfigurationName = instanceTemplate?.name
    googleServerGroup.launchConfig.instanceType = instanceTemplate?.properties?.machineType

    def sourceImage = instanceTemplate?.properties?.disks?.find { disk ->
      disk.boot
    }?.initializeParams?.sourceImage

    if (sourceImage) {
      googleServerGroup.launchConfig.imageId = Utils.getLocalName(sourceImage)
    }

    def instanceMetadata = instanceTemplate?.properties?.metadata

    if (instanceMetadata) {
      def metadataMap = Utils.buildMapFromMetadata(instanceMetadata)

      if (metadataMap) {
        def base64EncodedMap = new JsonBuilder(metadataMap).toString().bytes.encodeBase64().toString()

        googleServerGroup.launchConfig.userData = base64EncodedMap

        if (metadataMap["load-balancer-names"]) {
          def loadBalancerNameList = metadataMap["load-balancer-names"].split(",")

          if (loadBalancerNameList) {
            googleServerGroup.asg.loadBalancerNames = loadBalancerNameList

            // Collect all load balancer names at the cluster level as well.
            loadBalancerNameList.each { loadBalancerName ->
              if (!googleCluster.loadBalancers.find { it.name == loadBalancerName }) {
                googleCluster.loadBalancers << new GoogleLoadBalancer(loadBalancerName, googleServerGroup.region)
              }
            }
          }
        }
      }
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
