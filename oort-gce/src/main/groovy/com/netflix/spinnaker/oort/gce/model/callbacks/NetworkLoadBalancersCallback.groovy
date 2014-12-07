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
import com.google.api.services.compute.model.ForwardingRuleAggregatedList
import org.apache.log4j.Logger

class NetworkLoadBalancersCallback<ForwardingRuleAggregatedList> extends JsonBatchCallback<ForwardingRuleAggregatedList> {
  protected static final Logger log = Logger.getLogger(this)

  private Map<String, List<String>> networkLoadBalancerMap

  public NetworkLoadBalancersCallback(Map<String, List<String>> networkLoadBalancerMap) {
    this.networkLoadBalancerMap = networkLoadBalancerMap
  }

  @Override
  void onSuccess(ForwardingRuleAggregatedList forwardingRuleAggregatedList, HttpHeaders responseHeaders) throws IOException {
    forwardingRuleAggregatedList?.items?.each { scope, forwardingRulesScopedList ->
      // This can return a scope 'global' as well.
      if (scope.startsWith("regions/")) {
        def forwardingRules = forwardingRulesScopedList?.forwardingRules

        if (forwardingRules) {
          // Strip off 'regions/' prefix.
          def region = scope.substring(8)

          // Network load balancer lists are keyed (at this level) by region.
          if (!networkLoadBalancerMap[region]) {
            networkLoadBalancerMap[region] = new ArrayList<String>()
          }

          forwardingRules?.each { forwardingRule ->
            networkLoadBalancerMap[region] << forwardingRule.name
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