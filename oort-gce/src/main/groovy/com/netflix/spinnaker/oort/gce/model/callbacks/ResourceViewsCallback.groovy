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

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.resourceviews.model.ZoneViewsListResourcesResponse
import com.netflix.spinnaker.oort.gce.model.GoogleServerGroup
import org.apache.log4j.Logger

class ResourceViewsCallback<ZoneViewsListResourcesResponse> extends JsonBatchCallback<ZoneViewsListResourcesResponse> {
  protected static final Logger log = Logger.getLogger(this)

  private String localZoneName
  private GoogleServerGroup googleServerGroup
  private String project
  private Compute compute
  private Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap

  public ResourceViewsCallback(String localZoneName,
                               GoogleServerGroup googleServerGroup,
                               String project,
                               Compute compute,
                               Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap) {
    this.localZoneName = localZoneName
    this.googleServerGroup = googleServerGroup
    this.project = project
    this.compute = compute
    this.instanceNameToGoogleServerGroupMap = instanceNameToGoogleServerGroupMap
  }

  @Override
  void onSuccess(ZoneViewsListResourcesResponse listResourcesResult, HttpHeaders responseHeaders) throws IOException {
    for (def listResource : listResourcesResult.getItems()) {
      def instanceName = Utils.getLocalName(listResource.resource)

      instanceNameToGoogleServerGroupMap[instanceName] = googleServerGroup
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}
