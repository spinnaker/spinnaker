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

package com.netflix.spinnaker.gate

import retrofit.http.*

interface Api {

  @GET("/applications")
  List getApplications()

  @GET("/applications/{name}")
  Map getApplication(@Path("name") String name)

  @GET("/applications/{name}/tasks")
  List getTasks(@Path("name") String name, @Query("limit") Integer limit, @Query("statuses") String statuses)

  @POST("/applications/{name}/tasks")
  Map createTask(@Path("name") String name, @Body Map body)
}
