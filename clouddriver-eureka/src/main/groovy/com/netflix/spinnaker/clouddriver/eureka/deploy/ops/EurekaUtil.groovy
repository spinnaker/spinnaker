/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.eureka.deploy.ops

import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import org.apache.http.impl.client.HttpClients
import retrofit.RestAdapter
import retrofit.client.ApacheClient

import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class EurekaUtil {

  static Eureka getWritableEureka(String endpoint, String region) {
    String eurekaEndpoint = endpoint.replaceAll(Pattern.quote('{{region}}'), region)
    new RestAdapter.Builder()
      .setEndpoint(eurekaEndpoint)
      .setClient(getApacheClient())
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .build().create(Eureka)
  }

  //Lazy-create apache client on request if there is a discoveryEnabled AmazonCredentials:
  private static final AtomicReference<ApacheClient> apacheClient = new AtomicReference<>(null)

  private static ApacheClient getApacheClient() {
    if (apacheClient.get() == null) {
      synchronized (apacheClient) {
        if (apacheClient.get() == null) {
          apacheClient.set(new ApacheClient(HttpClients.createDefault()))
        }
      }
    }
    return apacheClient.get()
  }
}
