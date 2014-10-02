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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestTemplate

class Front50ApplicationCachingAgent extends AbstractInfrastructureCachingAgent {
  @Autowired
  RestTemplate restTemplate

  Front50ApplicationCachingAgent(NetflixAmazonCredentials account, String region) {
    super(account, region)
  }

  private Set<String> lastKnown = []

  @Override
  void load() {
    try {
      def list = (List<Map<String, String>>) restTemplate.getForObject("${account.front50}/applications", List)
      log.info "${cachePrefix} - Loading data for ${list.size()} applications."
      def appsThisRun = []
      for (Map<String, String> input in list) {
        def appName = input.name.toLowerCase()
        appsThisRun << appName
        AmazonApplication application = cacheService.retrieve(Keys.getApplicationKey("${Keys.Namespace.APPLICATIONS}:${appName}"), AmazonApplication) ?: new AmazonApplication(name: appName, attributes: [:])
        application.attributes += input
        cacheService.put(Keys.getApplicationKey(application.name), application)
      }
      def missingApps = lastKnown - appsThisRun
      for (app in missingApps) {
        cacheService.free(Keys.getApplicationKey(app))
      }
      lastKnown = appsThisRun
    } catch (e) {
      log.error "ERROR LOADING APPLICATION METADATA FROM FRONT50!!", e
    }
  }

  def getCachePrefix() {
    "[caching:${region}:${account.name}:f50]"
  }
}
