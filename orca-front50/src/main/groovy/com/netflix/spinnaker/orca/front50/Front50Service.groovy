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




package com.netflix.spinnaker.orca.front50

import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.front50.model.Front50Credential
import retrofit.client.Response
import retrofit.http.*

interface Front50Service {
  @GET("/credentials")
  List<Front50Credential> getCredentials()

  @GET("/{account}/applications/name/{name}")
  Application get(@Path("account") String account, @Path("name") String name)

  @POST("/{account}/applications/name/{name}")
  Response create(@Path("account") String account, @Path("name") String name, @Body Application application)

  @DELETE("/{account}/applications/name/{name}")
  Response delete(@Path("account") String account, @Path("name") String name)

  @PUT("/{account}/applications")
  Response update(@Path("account") String account, @Body Application application)

  @GET("/pipelines/{application}")
  List<Map<String, Object>> getPipelines(@Path("application") String application)

  @GET("/pipelines")
  List<Map<String, Object>> getAllPipelines()
}
