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


package com.netflix.spinnaker.orca.tide

import com.netflix.spinnaker.orca.tide.model.TideTask
import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query

interface TideService {

  @GET("/task/{taskId}")
  TideTask getTask(@Path("taskId") String taskId)

  @POST("/resource/serverGroup/{account}/{region}/{name}/deepCopy")
  String deepCopyServerGroup(
      @Path("account") String account,
      @Path("region") String region,
      @Path("name") String name,
      @Query("dryRun") Boolean dryRun,
      @Body Map target)

}
