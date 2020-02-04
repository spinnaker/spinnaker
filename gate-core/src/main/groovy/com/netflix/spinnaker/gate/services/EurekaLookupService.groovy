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
import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.model.discovery.DiscoveryApplication
import com.netflix.spinnaker.gate.retrofit.Slf4jRetrofitLogger
import com.netflix.spinnaker.gate.services.internal.EurekaService
import groovy.transform.Immutable
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.converter.JacksonConverter

import javax.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static retrofit.Endpoints.newFixedEndpoint

@Component
class EurekaLookupService {
  private static final Map<String, CachedDiscoveryApplication> instanceCache = new ConcurrentHashMap<>()

  @Autowired
  ServiceConfiguration serviceConfiguration

  @Autowired
  OkHttpClient okHttpClient

  @PostConstruct
  void init() {
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate({
      for (vip in instanceCache.keySet()) {
        def cached = instanceCache[vip]
        if (cached.expired) {
          getApplications(vip)
        }
      }
    }, 0, 30, TimeUnit.SECONDS)
  }

  List<DiscoveryApplication> getApplications(String vip) {
    if (instanceCache.containsKey(vip) && !instanceCache[vip].expired) {
      return instanceCache[vip].applications
    }
    List<String> hosts = []
    hosts.addAll(serviceConfiguration.discoveryHosts)
    Collections.shuffle(hosts)

    def app = null
    for (host in hosts) {
      EurekaService eureka = getEurekaService(host)
      try {
        app = eureka.getVips(vip)
        if (app && app.applications) {
          instanceCache[vip] = new CachedDiscoveryApplication(applications: app.applications)
          break
        }
      } catch (RetrofitError e) {
        if (e.response.status != 404) {
          throw e
        }
      }
    }
    if (!app) {
      return null
    }
    app.applications
  }

  private EurekaService getEurekaService(String host) {
    def endpoint = newFixedEndpoint(host)
    new RestAdapter.Builder()
        .setEndpoint(endpoint)
        .setConverter(new JacksonConverter(new ObjectMapper().configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)))
        .setClient(new Ok3Client(okHttpClient))
        .setLogLevel(RestAdapter.LogLevel.BASIC)
        .setLog(new Slf4jRetrofitLogger(EurekaService))
        .build()
        .create(EurekaService)
  }

  @Immutable(knownImmutables = ["applications"])
  static class CachedDiscoveryApplication {
    private final Long ttl = TimeUnit.SECONDS.toMillis(60)
    private final Long cacheTime = System.currentTimeMillis()
    final List<DiscoveryApplication> applications

    boolean isExpired() {
      (System.currentTimeMillis() - cacheTime) > ttl
    }
  }
}
