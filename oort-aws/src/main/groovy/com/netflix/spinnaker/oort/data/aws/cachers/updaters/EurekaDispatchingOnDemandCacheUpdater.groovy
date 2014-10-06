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

package com.netflix.spinnaker.oort.data.aws.cachers.updaters

import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.discovery.shared.LookupService
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

import javax.annotation.PostConstruct

/**
 * An on-demand cache updater that synchronizes with the rest of the cluster
 */
@Component
class EurekaDispatchingOnDemandCacheUpdater implements OnDemandCacheUpdater {
  private static final String EUREKA_NAME = "oort"
  private static final String ROUTE = "/cache/update"

  @Autowired
  LookupService lookupService

  @Autowired
  ApplicationInfoManager appInfoManager

  @Autowired
  RestTemplate restTemplate

  private String myCluster

  private final HttpHeaders jsonHeaders = new HttpHeaders() {
    {
      put "Content-Type", ["application/json"]
    }
  }

  @PostConstruct
  void init() {
    if (appInfoManager.info.ASGName) {
      Names names = Names.parseName(appInfoManager.info.ASGName)
      myCluster = names.cluster
    }
  }

  @Override
  boolean handles(String type) {
    true
  }

  @SuppressWarnings("GroovyAssignabilityCheck")
  @Override
  void handle(String type, Map<String, ? extends Object> data) {
    for (instance in lookupService.getApplication(EUREKA_NAME)?.instances) {
      def cluster = Names.parseName(instance.ASGName)
      if (myCluster && myCluster != cluster) {
        continue
      }
      if (!data.containsKey("source_oort")) {
        transmit instance.homePageUrl, data
      }
    }
  }

  private void transmit(String homePageUrl, Map<String, ? extends Object> data) {
    try {
      restTemplate.exchange("${homePageUrl}${ROUTE}", HttpMethod.POST, new HttpEntity(jsonHeaders), Map, data)
    } catch (IGNORE) { /* YOLO */
    }
  }
}
