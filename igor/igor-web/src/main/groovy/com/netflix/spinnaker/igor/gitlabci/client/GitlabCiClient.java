/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.gitlabci.client;

import com.netflix.spinnaker.igor.gitlabci.client.model.Bridge;
import com.netflix.spinnaker.igor.gitlabci.client.model.Job;
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline;
import com.netflix.spinnaker.igor.gitlabci.client.model.Project;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface GitlabCiClient {

  @GET("api/v4/projects")
  Call<List<Project>> getProjects(
      @Query("membership") boolean limitByMembership,
      @Query("owned") boolean limitByOwnership,
      @Query("page") int page,
      @Query("per_page") int pageLimit);

  @GET("api/v4/projects/{projectId}")
  Call<Project> getProject(@Path("projectId") String projectId);

  @GET("api/v4/projects/{projectId}/pipelines")
  Call<List<Pipeline>> getPipelineSummaries(
      @Path("projectId") String projectId, @Query("per_page") int pageLimit);

  @GET("api/v4/projects/{projectId}/pipelines/{pipelineId}")
  Call<Pipeline> getPipeline(
      @Path("projectId") String projectId, @Path("pipelineId") long pipelineId);

  @GET("api/v4/projects/{projectId}/pipelines/{pipelineId}/jobs")
  Call<List<Job>> getJobs(@Path("projectId") String projectId, @Path("pipelineId") long pipelineId);

  @GET("api/v4/projects/{projectId}/jobs/{jobId}/trace")
  Call<ResponseBody> getJobLog(@Path("projectId") String projectId, @Path("jobId") long jobId);

  // GitLabCI pipelines can spawn other child pipelines, which are linked by bridges
  @GET("api/v4/projects/{projectId}/pipelines/{pipelineId}/bridges")
  Call<List<Bridge>> getBridges(
      @Path("projectId") String projectId, @Path("pipelineId") long pipelineId);
}
