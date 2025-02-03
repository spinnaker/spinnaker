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

import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplication
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

interface Eureka {
  @Headers('Accept: application/json')
  @GET('/instances/{instanceId}')
  Call<Map> getInstanceInfo(@Path('instanceId') String instanceId)

  @Headers('Accept: application/json')
  @PUT('/apps/{application}/{instanceId}/status')
  Call<ResponseBody> updateInstanceStatus(@Path('application') String application, @Path('instanceId') String instanceId, @Query('value') String status)

  @Headers('Accept: application/json')
  @DELETE('/apps/{application}/{instanceId}/status')
  Call<ResponseBody> resetInstanceStatus(@Path('application') String application, @Path('instanceId') String instanceId, @Query('value') String status)

  @Headers('Accept: application/json')
  @GET('/apps/{application}')
  Call<EurekaApplication> getApplication(@Path('application') String application)
}
