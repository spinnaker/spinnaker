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
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.HttpHealthCheck
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import org.apache.log4j.Logger

class HttpHealthCheckCallback<HttpHealthCheck> extends JsonBatchCallback<HttpHealthCheck> {
  protected static final Logger log = Logger.getLogger(this)

  private GoogleLoadBalancer googleLoadBalancer
  private String project
  private Compute compute

  public HttpHealthCheckCallback(GoogleLoadBalancer googleLoadBalancer,
                                 String project,
                                 Compute compute) {
    this.googleLoadBalancer = googleLoadBalancer
    this.project = project
    this.compute = compute
  }

  @Override
  void onSuccess(HttpHealthCheck httpHealthCheck, HttpHeaders responseHeaders) throws IOException {
    def healthCheck = [:]

    if (httpHealthCheck.port != null) {
      def path = httpHealthCheck.requestPath ? httpHealthCheck.requestPath : "/"
      def target = "HTTP:$httpHealthCheck.port$path"

      healthCheck.target = target.toString()
    }

    if (httpHealthCheck.checkIntervalSec != null) {
      healthCheck.interval = httpHealthCheck.checkIntervalSec
    }

    if (httpHealthCheck.timeoutSec != null) {
      healthCheck.timeout = httpHealthCheck.timeoutSec
    }

    if (httpHealthCheck.unhealthyThreshold != null) {
      healthCheck.unhealthyThreshold = httpHealthCheck.unhealthyThreshold
    }

    if (httpHealthCheck.healthyThreshold != null) {
      healthCheck.healthyThreshold = httpHealthCheck.healthyThreshold
    }

    googleLoadBalancer.setProperty("healthCheck", healthCheck)
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }
}