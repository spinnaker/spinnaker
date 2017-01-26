/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.eureka.api

import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.Headers
import retrofit.http.PUT
import retrofit.http.DELETE
import retrofit.http.Path
import retrofit.http.Query

interface Eureka {
  @Headers('Accept: application/json')
  @GET('/instances/{instanceId}')
  Map getInstanceInfo(@Path('instanceId') String instanceId)

  @Headers('Accept: application/json')
  @PUT('/apps/{application}/{instanceId}/status')
  Response updateInstanceStatus(@Path('application') String application, @Path('instanceId') String instanceId, @Query('value') String status)

  @Headers('Accept: application/json')
  @DELETE('/apps/{application}/{instanceId}/status')
  Response resetInstanceStatus(@Path('application') String application, @Path('instanceId') String instanceId, @Query('value') String status)

}
