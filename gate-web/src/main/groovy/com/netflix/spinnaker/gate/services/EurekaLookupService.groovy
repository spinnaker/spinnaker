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

package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.gate.services.internal.EurekaService
import groovy.transform.Immutable
import java.util.concurrent.*
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter


import static retrofit.Endpoints.newFixedEndpoint

@Component
class EurekaLookupService {
  private static final Map<String, CachedDiscoveryApplication> instanceCache = new ConcurrentHashMap<>()

  @Autowired
  ServiceConfiguration serviceConfiguration

  @PostConstruct
  void init() {
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate({
      for (vip in instanceCache.keySet()) {
        def cached = instanceCache[vip]
        if (cached.expired) {
          getApplication(vip)
        }
      }
    }, 0, 30, TimeUnit.SECONDS)
  }

  DiscoveryApplication getApplication(String vip) {
    if (instanceCache.containsKey(vip) && !instanceCache[vip].expired) {
      return instanceCache[vip].application
    }
    List<String> hosts = []
    hosts.addAll(serviceConfiguration.discoveryHosts)
    Collections.shuffle(hosts)

    def app = null
    for (host in hosts) {
      EurekaService eureka = getEurekaService(host)
      try {
        app = eureka.getApplication(vip)
        if (app) {
          instanceCache[vip] = new CachedDiscoveryApplication(application: app)
          break
        }
      } catch (RetrofitError e) {
        if (e.response.status != 404) {
          throw e
        }
      }
    }
    app
  }

  private static EurekaService getEurekaService(String host) {
    def endpoint = newFixedEndpoint(host)
    new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setConverter(new JacksonConverter(new ObjectMapper().configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)))
        .setClient(new OkClient())
        .setLogLevel(RestAdapter.LogLevel.FULL)
        .build()
        .create(EurekaService)
  }

  @Immutable(knownImmutables = ["application"])
  static class CachedDiscoveryApplication {
    private final Long ttl = TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS)
    private final Long cacheTime = System.currentTimeMillis()
    final DiscoveryApplication application

    boolean isExpired() {
      (System.currentTimeMillis() - cacheTime) > ttl
    }
  }
}
