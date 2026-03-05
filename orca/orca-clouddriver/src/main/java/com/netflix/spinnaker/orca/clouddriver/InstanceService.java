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

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;

public interface InstanceService {
  // TODO: add concrete result objects vs Response objects
  @GET("tasks")
  Call<ResponseBody> listTasks();

  @GET("tasks/{id}")
  Call<ResponseBody> listTask(@Path("id") String id);

  @PATCH("{app}/{version}")
  Call<ResponseBody> patchInstance(
      @Path("app") String app, @Path("version") String version, @Body String body);

  @GET("{app}/current")
  Call<ResponseBody> getCurrentVersion(@Path("app") String app);

  @GET("{healthCheckPath}")
  Call<ResponseBody> healthCheck(
      @Path(value = "healthCheckPath", encoded = true) String healthCheckPath);

  @GET("v1/platform/base/jars")
  Call<ResponseBody> getJars();
}
