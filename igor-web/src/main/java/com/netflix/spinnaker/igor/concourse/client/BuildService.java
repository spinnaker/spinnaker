/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse.client;

import com.netflix.spinnaker.igor.concourse.client.model.Build;
import com.netflix.spinnaker.igor.concourse.client.model.Plan;
import java.util.List;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public interface BuildService {
  @GET("/api/v1/teams/{team}/pipelines/{pipeline}/jobs/{job}/builds")
  List<Build> builds(
      @Path("team") String team,
      @Path("pipeline") String pipeline,
      @Path("job") String job,
      @Query("limit") Integer limit,
      @Query("since") Long since);

  @GET("/api/v1/builds/{id}/plan")
  Plan plan(@Path("id") String id);
}
