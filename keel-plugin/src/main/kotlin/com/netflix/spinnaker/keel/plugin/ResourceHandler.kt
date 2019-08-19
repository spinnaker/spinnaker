/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.Resource

interface ResourceHandler<T : ResourceSpec> : ResolvableResourceHandler<T, T> {

  /**
   * Don't override this method, just implement [current]. If you need to do any resolution of the
   * desired value you should implement [ResolvableResourceHandler] instead of this interface.
   */
  override suspend fun desired(resource: Resource<T>): T = resource.spec
}
