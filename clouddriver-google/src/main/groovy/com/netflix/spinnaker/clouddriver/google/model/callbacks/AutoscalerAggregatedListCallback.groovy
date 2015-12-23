/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model.callbacks

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.Autoscaler
import com.google.api.services.compute.model.AutoscalerAggregatedList
import com.google.api.services.compute.model.AutoscalersScopedList
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.google.model.GoogleApplication
import org.apache.log4j.Logger

class AutoscalerAggregatedListCallback<AutoscalerAggregatedList> extends JsonBatchCallback<AutoscalerAggregatedList> {
  protected static final Logger log = Logger.getLogger(this)

  private HashMap<String, GoogleApplication> tempAppMap
  private String accountName

  public AutoscalerAggregatedListCallback(HashMap<String, GoogleApplication> tempAppMap, String accountName) {
    this.tempAppMap = tempAppMap
    this.accountName = accountName
  }

  @Override
  void onSuccess(AutoscalerAggregatedList autoscalerAggregatedList, HttpHeaders responseHeaders) throws IOException {
    autoscalerAggregatedList?.items.each { String location, AutoscalersScopedList autoscalersScopedList ->
      if (location.startsWith("zones/")) {
        def localZoneName = Utils.getLocalName(location)
        def region = localZoneName.substring(0, localZoneName.lastIndexOf('-'))

        autoscalersScopedList.autoscalers.each { Autoscaler autoscaler ->
          def migName = Utils.getLocalName(autoscaler.target)
          def names = Names.parseName(migName)
          def cluster = Utils.retrieveOrCreatePathToCluster(tempAppMap, accountName, names.app, names.cluster)
          def serverGroup = cluster.serverGroups.find {
            it.name == migName && it.region == region
          }

          if (serverGroup) {
            serverGroup.autoscalingPolicy = autoscaler.autoscalingPolicy
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
