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

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.Reactor

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class AbstractInfrastructureCachingAgent implements InfrastructureCachingAgent {
  protected static final Logger log = Logger.getLogger(this)

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  CacheService cacheService

  @Autowired
  Reactor reactor

  final AmazonNamedAccount account
  final String region

  AbstractInfrastructureCachingAgent(AmazonNamedAccount account, String region) {
    this.account = account
    this.region = region
  }

  @PostConstruct
  void init() {
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
      try {
        load()
      } catch (Throwable t) {
        t.printStackTrace()
      }
    }, 0, 60, TimeUnit.SECONDS)
  }

  abstract void load()
}
