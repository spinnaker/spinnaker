/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.registry

import com.netflix.spinnaker.rosco.api.BakeRequest
import com.netflix.spinnaker.rosco.providers.CloudProviderBakeHandler

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple implementation of CloudProviderBakeHandlerRegistry backed by a ConcurrentHashMap.
 */
class DefaultCloudProviderBakeHandlerRegistry implements CloudProviderBakeHandlerRegistry {

  private Map<BakeRequest.CloudProviderType, CloudProviderBakeHandler> map =
    new ConcurrentHashMap<BakeRequest.CloudProviderType, CloudProviderBakeHandler>()
  private BakeRequest.CloudProviderType defaultCloudProviderType

  public DefaultCloudProviderBakeHandlerRegistry(BakeRequest.CloudProviderType defaultCloudProviderType) {
    this.defaultCloudProviderType = defaultCloudProviderType
  }

  @Override
  void register(BakeRequest.CloudProviderType cloudProviderType, CloudProviderBakeHandler cloudProviderBakeHandler) {
    map[cloudProviderType] = cloudProviderBakeHandler
  }

  @Override
  CloudProviderBakeHandler lookup(BakeRequest.CloudProviderType cloudProviderType) {
    if (!cloudProviderType) {
      cloudProviderType = defaultCloudProviderType
    }

    return map[cloudProviderType]
  }

  @Override
  public CloudProviderBakeHandler findProducer(String logsContentFirstLine) {
    map.values().find { cloudProviderBakeHandler ->
      cloudProviderBakeHandler.isProducerOf(logsContentFirstLine)
    }
  }

}
