/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.common

import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription

class AzureUtilities {

  static String getResourceNameFromID(String resourceId) {
    int idx = resourceId.lastIndexOf('/')
    if (idx > 0) {
      return resourceId.substring(idx + 1)
    }
    resourceId
  }

  static String getResourceGroupName(AzureLoadBalancerDescription description) {
    description.appName + "_" + description.region
  }

  static String getResourceGroupName(String appName, String region) {
    appName + "_" + region.replace(' ', '').toLowerCase()
  }

}
