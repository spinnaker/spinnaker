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

package com.netflix.spinnaker.clouddriver.aws.discovery

import com.netflix.spinnaker.clouddriver.aws.model.discovery.DiscoveryApplications
import retrofit.http.GET
import retrofit.http.Headers

interface DiscoveryApi {

  @GET('/v2/apps')
  @Headers(['Accept: application/json'])
  DiscoveryApplications loadDiscoveryApplications()
}
