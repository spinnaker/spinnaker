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

/**
 * Each platform-specific CloudProviderBakeHandler should be registered with this registry.
 * This registry is intended for use by consumers such as the BakeryController.
 */
interface CloudProviderBakeHandlerRegistry {

  /**
   * Define an association between the specified CloudProviderType and the CloudProviderBakeHandler.
   */
  public void register(BakeRequest.CloudProviderType cloudProviderType, CloudProviderBakeHandler cloudProviderBakeHandler)

  /**
   * Return a CloudProviderBakeHandler capable of handling bake requests for the specified CloudProviderType.
   */
  public CloudProviderBakeHandler lookup(BakeRequest.CloudProviderType cloudProviderType)

  /**
   * Return all registered CloudProviderBakeHandlers.
   */
  public List<CloudProviderBakeHandler> list()

}