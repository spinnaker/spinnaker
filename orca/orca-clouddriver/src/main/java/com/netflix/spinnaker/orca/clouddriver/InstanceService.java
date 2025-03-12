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

package com.netflix.spinnaker.orca.clouddriver;

import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PATCH;
import retrofit.http.Path;

public interface InstanceService {
  // TODO: add concrete result objects vs Response objects
  @GET("/tasks")
  Response listTasks();

  @GET("/tasks/{id}")
  Response listTask(@Path("id") String id);

  @PATCH("/{app}/{version}")
  Response patchInstance(
      @Path("app") String app, @Path("version") String version, @Body String body);

  @GET("/{app}/current")
  Response getCurrentVersion(@Path("app") String app);

  @GET("/{healthCheckPath}")
  Response healthCheck(@Path(value = "healthCheckPath", encode = false) String healthCheckPath);

  @GET("/v1/platform/base/jars")
  Response getJars();
}
