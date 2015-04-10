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
import com.google.api.services.compute.model.Region
import com.google.api.services.replicapool.Replicapool
import com.netflix.spinnaker.oort.gce.model.GoogleApplication
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import org.apache.log4j.Logger

class RegionsCallback<Region> extends JsonBatchCallback<Region> {
  protected static final Logger log = Logger.getLogger(this)

  private HashMap<String, GoogleApplication> tempAppMap
  private String accountName
  private String project
  private Compute compute
  private GoogleCredential.Builder credentialBuilder
  private Replicapool replicapool
  private Map<String, List<Map>> imageMap
  private String defaultBuildHost
  private Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap
  private BatchRequest migsBatch
  private BatchRequest resourceViewsBatch

  public RegionsCallback(HashMap<String, GoogleApplication> tempAppMap,
                         String accountName,
                         String project,
                         Compute compute,
                         GoogleCredential.Builder credentialBuilder,
                         Replicapool replicapool,
                         Map<String, List<Map>> imageMap,
                         String defaultBuildHost,
                         Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap,
                         BatchRequest migsBatch,
                         BatchRequest resourceViewsBatch) {
    this.tempAppMap = tempAppMap
    this.accountName = accountName
    this.project = project
    this.compute = compute
    this.credentialBuilder = credentialBuilder
    this.replicapool = replicapool
    this.imageMap = imageMap
    this.defaultBuildHost = defaultBuildHost
    this.instanceNameToGoogleServerGroupMap = instanceNameToGoogleServerGroupMap
    this.migsBatch = migsBatch
    this.resourceViewsBatch = resourceViewsBatch
  }

  @Override
  void onSuccess(Region region, HttpHeaders responseHeaders) throws IOException {
    def zones = region.getZones()

    zones.each { zone ->
      def localZoneName = Utils.getLocalName(zone)
      def migsCallback = new MIGSCallback(tempAppMap,
                                          region.getName(),
                                          localZoneName,
                                          accountName,
                                          project,
                                          compute,
                                          credentialBuilder,
                                          imageMap,
                                          defaultBuildHost,
                                          instanceNameToGoogleServerGroupMap,
                                          resourceViewsBatch)

      replicapool.instanceGroupManagers().list(project, localZoneName).queue(migsBatch, migsCallback)
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}