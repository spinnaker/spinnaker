/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.retrofit

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.gate.services.EurekaLookupService
import com.squareup.okhttp.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import retrofit.client.OkClient
import retrofit.client.Request
import retrofit.client.Response

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class EurekaOkClient extends OkClient {
  static final Pattern NIWS_SCHEME_PATTERN = ~("niws://([^/]+)(.*)")

  private final EurekaLookupService eureka
  private final Registry registry
  private final Id metricId;
  private final Id discoveryId;

  @Autowired
  EurekaOkClient(OkHttpClient okHttpClient, Registry registry, String name, EurekaLookupService eureka) {
    super(okHttpClient)
    this.registry = registry
    this.eureka = eureka
    this.metricId = registry.createId("EurekaOkClient_Request").withTag("service", name)
    this.discoveryId = registry.createId("EurekaOkClient_Discovery").withTag("service", name)
  }

  @Override
  Response execute(Request req) throws IOException {
    def matcher = req.url =~ NIWS_SCHEME_PATTERN
    if (matcher.matches()) {
      String vip = matcher[0][1]
      String path = ""
      if (matcher[0].size() > 2) {
        path = matcher[0][2]
      }

      String success = "false"
      String cause = null
      long started = System.nanoTime()
      try {
        def apps = eureka.getApplications(vip)
        def randomInstance = DiscoveryApplication.getRandomUpInstance(apps)
        if (!randomInstance) {
          throw new NoSuchElementException("Error resolving Eureka UP instance for ${vip}!")
        }
        req = new Request(req.method, "http://${randomInstance.hostName}:${randomInstance.port.port}${path}", req.headers, req.body)
        success = "true"
      } catch (e) {
        cause = e.class.simpleName
        throw e
      } finally {
        registry.timer((cause ? discoveryId.withTag('cause', cause) : discoveryId).withTag('success', success)).record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
      }
    }

    long start = System.nanoTime()
    String cause = null
    int status = -1
    try {
      def response = super.execute(req)
      status = response.status
      return response
    } catch (e) {
      cause = e.class.simpleName
      throw e
    } finally {
      registry.timer(buildMetric(cause, status)).record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
  }

  private Id buildMetric(String cause, int status) {
    def id = (cause == null ? metricId : metricId.withTag('cause', cause))
      .withTag('statusCode', Integer.toString(status))
      .withTag('status', httpBucketForResponse(status))
      .withTag('success', Boolean.toString(cause == null && status >= 100 && status < 400))
    id
  }

  private static String httpBucketForResponse(int httpCode) {
    if (httpCode < 0) {
      return "Unknown"
    }
    if (httpCode < 100 || httpCode >= 600) {
      return "Invalid"
    }
    return "${Integer.toString(httpCode)[0]}xx"
  }
}
