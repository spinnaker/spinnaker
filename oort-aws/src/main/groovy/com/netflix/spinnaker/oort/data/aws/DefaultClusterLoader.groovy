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

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.config.OortDefaults
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import org.apache.directmemory.cache.CacheService
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class DefaultClusterLoader implements ApplicationListener<AmazonDataLoadEvent> {
  private static final Logger log = Logger.getLogger(this)
  private final ExecutorService clusterLoaderExecutor = Executors.newFixedThreadPool(200)

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  NamedAccountProvider namedAccountProvider

  @Autowired
  RestTemplate restTemplate

  @Autowired
  OortDefaults oortDefaults

  @Autowired
  CacheService<String, Object> cacheService

  @Override
  void onApplicationEvent(AmazonDataLoadEvent event) {
    log.info "Loading Cluster Data"
    def loader = loader.curry(event)
    clusterLoaderExecutor.submit(loader)
  }

  private final Closure loader = { AmazonDataLoadEvent event ->
    try {
      def asg = event.autoScalingGroup
      def names = Names.parseName(asg.autoScalingGroupName)
      def cluster = (AmazonCluster) cacheService.retrieve(Keys.getClusterKey(names.cluster, names.app, event.amazonNamedAccount.name))
      if (!cluster) {
        log.info "Adding new cluster ${event.amazonNamedAccount.name} ${names.cluster} ${names.group}"
        cluster = new AmazonCluster(name: names.cluster, accountName: event.amazonNamedAccount.name)
      }
      cacheService.put Keys.getClusterKey(names.cluster, names.app, event.amazonNamedAccount.name), cluster, 300000
    } catch (e) {
      log.error(e)
    }
  }

  protected void shutdownAndWait(int seconds) {
    clusterLoaderExecutor.shutdown()
    clusterLoaderExecutor.awaitTermination(seconds, TimeUnit.SECONDS)
  }

}
