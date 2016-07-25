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
import com.squareup.okhttp.Response
import retrofit.http.*

interface AgentApi {
  @GET("/v1/agent/checks")
  Map<String, CheckResult> checks()

  @GET("/v1/agent/services")
  Map<String, ServiceResult> services()

  @GET("/v1/agent/self")
  AgentDefinition self()

  @GET("/v1/agent/join/{address}")
  Response join(@Path("address") String address, @Query("wan") Integer wan)

  @PUT("/v1/agent/check/register")
  Response registerCheck(@Body CheckDefinition check, @Query("token") String tokenId)

  @PUT("/v1/agent/check/deregister/{checkId}")
  Response deregisterCheck(@Path("checkId") String checkId)

  @PUT("/v1/agent/service/register")
  Response registerService(@Body ServiceDefinition service, @Query("token") String tokenId)

  @PUT("/v1/agent/service/deregister/{serviceId}")
  Response deregisterService(@Path("serviceId") String serviceId)

  @PUT("/v1/agent/service/maintenance/{serviceId}")
  Response maintenance(@Path("serviceId") String serviceId, @Query("enable") boolean enable, @Query("reason") String reason)

  @PUT("/v1/agent/maintenance")
  Response maintenance(@Query("enable") boolean enable, @Query("reason") String reason)
}
