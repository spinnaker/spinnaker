/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.consul.api.v1.services

import com.netflix.spinnaker.clouddriver.consul.api.v1.model.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AgentApi {
  @GET("/v1/agent/checks")
  Call<Map<String, CheckResult>> checks()

  @GET("/v1/agent/services")
  Call<Map<String, ServiceResult>> services()

  @GET("/v1/agent/self")
  Call<AgentDefinition> self()

  @GET("/v1/agent/join/{address}")
  Call<ResponseBody> join(@Path("address") String address, @Query("wan") Integer wan)

  @PUT("/v1/agent/check/register")
  Call<ResponseBody> registerCheck(@Body CheckDefinition check, @Query("token") String tokenId)

  @PUT("/v1/agent/check/deregister/{checkId}")
  Call<ResponseBody> deregisterCheck(@Path("checkId") String checkId)

  @PUT("/v1/agent/service/register")
  Call<ResponseBody> registerService(@Body ServiceDefinition service, @Query("token") String tokenId)

  @PUT("/v1/agent/service/deregister/{serviceId}")
  Call<ResponseBody> deregisterService(@Path("serviceId") String serviceId)

  @PUT("/v1/agent/service/maintenance/{serviceId}")
  Call<ResponseBody> maintenance(@Path("serviceId") String serviceId, @Query("enable") boolean enable, @Query("reason") String reason)

  @PUT("/v1/agent/maintenance")
  Call<ResponseBody> maintenance(@Query("enable") boolean enable, @Query("reason") String reason, @Body String _empty /* Retrofit requires a body, even if it's empty... */)
}
