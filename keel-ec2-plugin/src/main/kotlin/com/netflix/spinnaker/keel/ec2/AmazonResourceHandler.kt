/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.ec2

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup

interface AmazonResourceHandler<S : Any> {
  /**
   * Retrieve the current state for the provided resource based on the [spec].
   */
  fun current(spec: S, request: Resource<S>): S?

  /**
   * Converge on the provided resource.
   *
   * @param resourceName is provided for use in correlation IDs.
   */
  fun converge(resourceName: ResourceName, spec: S)

  /**
   * Delete a resource.

   * @param resourceName is provided for use in correlation IDs.
   */
  fun delete(resourceName: ResourceName, spec: SecurityGroup)
}
