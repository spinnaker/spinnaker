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

package com.netflix.spinnaker.orca.kato.api

import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import rx.Observable

/**
 * An interface to the Kato REST API for Amazon cloud. See {@link http://kato.test.netflix.net:7001/manual/index.html}.
 */
interface KatoService {

  @POST("/ops")
  Observable<TaskId> requestOperations(@Body Collection<Operation> operations)

  @GET("/task")
  Observable<List<Task>> listTasks()

  @GET("/task/{id}")
  Observable<Task> lookupTask(@Path("id") String id)

}