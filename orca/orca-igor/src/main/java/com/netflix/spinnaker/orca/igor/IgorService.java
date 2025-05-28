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
package com.netflix.spinnaker.orca.igor;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild;
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuildRepoSource;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface IgorService {
  @PUT("/masters/{name}/jobs/{jobName}")
  Call<ResponseBody> build(
      @Path("name") String master,
      @Path(encoded = true, value = "jobName") String jobName,
      @QueryMap Map<String, String> queryParams,
      @Body String startTime);

  @PUT("/masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}")
  Call<String> stop(
      @Path("name") String master,
      @Path(encoded = true, value = "jobName") String jobName,
      @Path(encoded = true, value = "queuedBuild") String queuedBuild,
      @Path(encoded = true, value = "buildNumber") Long buildNumber,
      @Body String ignored);

  @PUT("/masters/{name}/jobs/stop/{queuedBuild}/{buildNumber}")
  Call<String> stopWithJobNameAsQueryParameter(
      @Path("name") String master,
      @Query(value = "jobName") String jobName,
      @Path(encoded = true, value = "queuedBuild") String queuedBuild,
      @Path(encoded = true, value = "buildNumber") Long buildNumber,
      @Body String ignored);

  @PATCH("/masters/{name}/jobs/{jobName}/update/{buildNumber}")
  Call<ResponseBody> update(
      @Path("name") String master,
      @Path(encoded = true, value = "jobName") String jobName,
      @Path(encoded = true, value = "buildNumber") Long buildNumber,
      @Body UpdatedBuild updatedBuild);

  @GET("/builds/queue/{master}/{item}")
  Call<Map> queuedBuild(@Path("master") String master, @Path("item") String item);

  @GET("/builds/status/{buildNumber}/{master}/{job}")
  Call<Map<String, Object>> getBuild(
      @Path("buildNumber") Long buildNumber,
      @Path("master") String master,
      @Path(encoded = true, value = "job") String job);

  @GET("/builds/status/{buildNumber}/{master}")
  Call<Map<String, Object>> getBuildWithJobAsQueryParam(
      @Path("buildNumber") Long buildNumber,
      @Path("master") String master,
      @Query(encoded = true, value = "job") String job);

  @GET("/builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Call<Map<String, Object>> getPropertyFile(
      @Path("buildNumber") Long buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Path(encoded = true, value = "job") String job);

  @GET("/builds/properties/{buildNumber}/{fileName}/{master}")
  Call<Map<String, Object>> getPropertyFileWithJobAsQueryParam(
      @Path("buildNumber") Long buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Query(encoded = true, value = "job") String job);

  @GET("/{repoType}/{projectKey}/{repositorySlug}/compareCommits")
  Call<List> compareCommits(
      @Path("repoType") String repoType,
      @Path("projectKey") String projectKey,
      @Path("repositorySlug") String repositorySlug,
      @QueryMap Map<String, String> requestParams);

  @GET("/builds/artifacts/{buildNumber}/{master}/{job}")
  Call<List<Artifact>> getArtifacts(
      @Path("buildNumber") Long buildNumber,
      @Path("master") String master,
      @Path(value = "job", encoded = true) String job,
      @Query("propertyFile") String propertyFile);

  @GET("/builds/artifacts/{buildNumber}/{master}")
  Call<List<Artifact>> getArtifactsWithJobAsQueryParam(
      @Path("buildNumber") Long buildNumber,
      @Path("master") String master,
      @Query(value = "job", encoded = true) String job,
      @Query("propertyFile") String propertyFile);

  @POST("/gcb/builds/create/{account}")
  Call<GoogleCloudBuild> createGoogleCloudBuild(
      @Path("account") String account, @Body Map<String, Object> job);

  @POST("/gcb/builds/stop/{account}/{buildId}")
  Call<GoogleCloudBuild> stopGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/gcb/builds/{account}/{buildId}")
  Call<GoogleCloudBuild> getGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/gcb/builds/{account}/{buildId}/artifacts")
  Call<List<Artifact>> getGoogleCloudBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("/gcb/triggers/{account}/{triggerId}/run")
  Call<GoogleCloudBuild> runGoogleCloudBuildTrigger(
      @Path("account") String account,
      @Path("triggerId") String triggerId,
      @Body GoogleCloudBuildRepoSource repoSource);

  @POST("/codebuild/builds/start/{account}")
  Call<AwsCodeBuildExecution> startAwsCodeBuild(
      @Path("account") String account, @Body Map<String, Object> requestInput);

  @GET("/codebuild/builds/{account}/{buildId}")
  Call<AwsCodeBuildExecution> getAwsCodeBuildExecution(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/codebuild/builds/artifacts/{account}/{buildId}")
  Call<List<Artifact>> getAwsCodeBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("/codebuild/builds/stop/{account}/{buildId}")
  Call<AwsCodeBuildExecution> stopAwsCodeBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/delivery-config/manifest")
  Call<Map<String, Object>> getDeliveryConfigManifest(
      @Query("scmType") String repoType,
      @Query("project") String projectKey,
      @Query("repository") String repositorySlug,
      @Query("directory") @Nullable String directory,
      @Query("manifest") @Nullable String manifest,
      @Query("ref") @Nullable String ref);

  @Data
  class UpdatedBuild {
    private final String description;

    public UpdatedBuild(String description) {
      this.description = description;
    }
  }
}
