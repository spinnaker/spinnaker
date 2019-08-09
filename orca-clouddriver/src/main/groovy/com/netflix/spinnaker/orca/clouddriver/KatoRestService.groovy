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

package com.netflix.spinnaker.orca.clouddriver

import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query

/**
 * An interface to the Kato REST API for Amazon cloud. See {@link http://kato.test.netflix.net:7001/manual/index.html}.
 */
interface KatoRestService {

  /**
   * @deprecated Use {@code /{cloudProvider}/ops} instead
   */
  @Deprecated
  @POST("/ops")
  TaskId requestOperations(@Query("clientRequestId") String clientRequestId,
                           @Body Collection<? extends Map<String, Map>> operations)

  @POST("/{cloudProvider}/ops")
  TaskId requestOperations(@Query("clientRequestId") String clientRequestId,
                           @Path("cloudProvider") String cloudProvider,
                           @Body Collection<? extends Map<String, Map>> operations)

  @POST("/applications/{app}/jobs/{account}/{region}/{id}")
  Response collectJob(@Path("app") String app,
                      @Path("account") String account,
                      @Path("region") String region,
                      @Path("id") String id)

  @DELETE("/applications/{app}/jobs/{account}/{location}/{id}")
  Response cancelJob(@Path("app") String app,
                     @Path("account") String account,
                     @Path("location") String region,
                     @Path("id") String id)

  @GET("/applications/{app}/jobs/{account}/{region}/{id}/{fileName}")
  Map<String, Object> getFileContents(@Path("app") String app,
                                      @Path("account") String account,
                                      @Path("region") String region,
                                      @Path("id") String id,
                                      @Path("fileName") String fileName)

  /**
   * This should _only_ be called if there is a problem retrieving the Task from CloudDriverTaskStatusService (ie. a
   * clouddriver replica).
   */
  @GET("/task/{id}")
  Task lookupTask(@Path("id") String id);
}
