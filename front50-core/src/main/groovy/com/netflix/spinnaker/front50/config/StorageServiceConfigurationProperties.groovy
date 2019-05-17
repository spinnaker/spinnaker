/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config

import org.springframework.boot.context.properties.ConfigurationProperties

import java.util.concurrent.TimeUnit;

@ConfigurationProperties("storage-service")
class StorageServiceConfigurationProperties {
  PerObjectType application = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType applicationPermission = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType serviceAccount = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType project = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType notification = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType pipelineStrategy = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType pipeline = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType pipelineTemplate = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))
  PerObjectType snapshot = new PerObjectType(2, TimeUnit.MINUTES.toMillis(1))
  PerObjectType deliveryConfig = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1))

  // not commonly used outside of Netflix
  PerObjectType entityTags = new PerObjectType(2, TimeUnit.MINUTES.toMillis(5), false)

  static class PerObjectType {
    int threadPool
    long refreshMs
    boolean shouldWarmCache

    PerObjectType(int threadPool, long refreshMs) {
      this(threadPool, refreshMs, true)
    }

    PerObjectType(int threadPool, long refreshMs, boolean shouldWarmCache) {
      setThreadPool(threadPool)
      setRefreshMs(refreshMs)
      setShouldWarmCache(shouldWarmCache)
    }

    void setThreadPool(int threadPool) {
      if (threadPool <= 1) {
        throw new IllegalArgumentException("threadPool must be >= 1")
      }
      this.threadPool = threadPool
    }
  }
}
