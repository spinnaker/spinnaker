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
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Loader
import com.netflix.spinnaker.oort.data.aws.MultiAccountCachingSupport
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger

@Component
class ImageCacher extends MultiAccountCachingSupport implements Loader {
  private static final Logger log = Logger.getLogger(this.class.simpleName)
  final ExecutorService executorService = Executors.newFixedThreadPool(50)

  @Autowired
  CacheService<String, Object> cacheService

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Async("taskScheduler")
  @Scheduled(fixedRate = 30000l)
  void load() {
    log.info "Caching images..."
    invokeMultiAccountMultiRegionClosure loadImagesCallable
  }

  final Closure loadImagesCallable = { AmazonNamedAccount account, String region ->
    def ec2 = amazonClientProvider.getAmazonEC2(account.credentials, region)
    def images = ec2.describeImages().images
    for (image in images) {
      cacheService.put(Keys.getImageKey(image.imageId, region), image)
    }
  }
}
