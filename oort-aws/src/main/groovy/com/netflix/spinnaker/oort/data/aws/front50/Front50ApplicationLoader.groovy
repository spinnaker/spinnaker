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

package com.netflix.spinnaker.oort.data.aws.front50

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.ApplicationLoader
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import groovy.transform.CompileStatic
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@CompileStatic
@Component("front50ApplicationLoader")
class Front50ApplicationLoader implements ApplicationLoader {
  private static final Logger log = Logger.getLogger(this)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  CacheService cacheService

  @Autowired
  RestTemplate restTemplate

  @Async
  @Scheduled(fixedRateString = '${cacheRefreshMs:60000}')
  void load() {
    log.info "Beginning Front50 Application Caching..."
    for (cred in accountCredentialsProvider.all) {
      if (!(cred instanceof NetflixAmazonCredentials)) {
        continue
      }
      def account = (NetflixAmazonCredentials) cred
      if (account.front50Enabled) {
        try {
          def list = (List<Map<String, String>>) restTemplate.getForObject("${account.front50}/applications", List)
          for (Map<String, String> input in list) {
            def appName = input.name.toLowerCase()
            AmazonApplication application = cacheService.retrieve(Keys.getApplicationKey("${Keys.Namespace.APPLICATIONS}:${appName}"), AmazonApplication) ?: new AmazonApplication(name: appName, attributes: [:])
            application.attributes += input
            cacheService.put(Keys.getApplicationKey(application.name), application)
          }
        } catch (e) {
          log.error "ERROR LOADING APPLICATION METADATA FROM FRONT50!!", e
        }
      }
    }
  }
}
