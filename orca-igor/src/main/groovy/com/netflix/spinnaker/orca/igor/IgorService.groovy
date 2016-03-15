/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor

import retrofit.http.EncodedPath
import retrofit.http.GET
import retrofit.http.PUT
import retrofit.http.Path
import retrofit.http.QueryMap

interface IgorService {

  @PUT("/masters/{name}/jobs/{jobName}")
  String build(@Path("name") String master, @EncodedPath("jobName") String jobName, @QueryMap Map<String,String> queryParams)

  @PUT("/masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}")
  String stop(@Path("name") String master, @EncodedPath("jobName") String jobName, @EncodedPath("queuedBuild") String queuedBuild, @EncodedPath("buildNumber") Integer buildNumber)

  @GET("/builds/queue/{master}/{item}")
  Map queuedBuild(@Path("master") String master, @Path("item") String item)

  @GET("/builds/status/{buildNumber}/{master}/{job}")
  Map<String, Object> getBuild(@Path("buildNumber") Integer buildNumber,
                               @Path("master") String master,
                               @EncodedPath("job") String job)

  @GET("/builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Map<String, Object> getPropertyFile(@Path("buildNumber") Integer buildNumber,
                                      @Path("fileName") String fileName,
                                      @Path("master") String master,
                                      @EncodedPath("job") String job)

  @GET("/{repoType}/{projectKey}/{repositorySlug}/compareCommits")
  List compareCommits(@Path("repoType") String repoType, @Path("projectKey") String projectKey, @Path("repositorySlug") String repositorySlug, @QueryMap Map<String, String> requestParams)

}
