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

package com.netflix.spinnaker.oort.data.aws

import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic
import org.apache.directmemory.cache.CacheService
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
  NamedAccountProvider namedAccountProvider

  @Autowired
  CacheService<String, AmazonApplication> applicationCacheService

  @Autowired
  RestTemplate restTemplate

  @Async("taskExecutor")
  @Scheduled(fixedRate = 30000l)
  void load() {
    log.info "Beginning Front50 Application Caching..."
    for (name in namedAccountProvider.accountNames) {
      def a = namedAccountProvider.get(name)
      if (!(a instanceof AmazonNamedAccount)) continue
      def account = (AmazonNamedAccount)a
      if (account.front50) {
        def list = (List<Map<String, String>>)restTemplate.getForObject("${account.front50}/applications", List)
        for (Map<String, String> input in list) {
          def appName = input.name.toLowerCase()
          def application = applicationCacheService.retrieve(appName) ?: new AmazonApplication(name:appName)
          application.attributes += input
          if (!applicationCacheService.put(application.name, application, 300000)) {
            log.info("Not enough space to save application!!")
          }
        }
      }
    }
  }
}
