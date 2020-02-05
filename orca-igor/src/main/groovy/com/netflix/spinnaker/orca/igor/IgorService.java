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
import retrofit.http.*;

public interface IgorService {
  @PUT("/masters/{name}/jobs/{jobName}")
  String build(
      @Path("name") String master,
      @Path(encode = false, value = "jobName") String jobName,
      @QueryMap Map<String, String> queryParams,
      @Body String ignored);

  @PUT("/masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}")
  String stop(
      @Path("name") String master,
      @Path(encode = false, value = "jobName") String jobName,
      @Path(encode = false, value = "queuedBuild") String queuedBuild,
      @Path(encode = false, value = "buildNumber") Integer buildNumber,
      @Body String ignored);

  @GET("/builds/queue/{master}/{item}")
  Map queuedBuild(@Path("master") String master, @Path("item") String item);

  @GET("/builds/status/{buildNumber}/{master}/{job}")
  Map<String, Object> getBuild(
      @Path("buildNumber") Integer buildNumber,
      @Path("master") String master,
      @Path(encode = false, value = "job") String job);

  @GET("/builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Map<String, Object> getPropertyFile(
      @Path("buildNumber") Integer buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Path(encode = false, value = "job") String job);

  @GET("/{repoType}/{projectKey}/{repositorySlug}/compareCommits")
  List compareCommits(
      @Path("repoType") String repoType,
      @Path("projectKey") String projectKey,
      @Path("repositorySlug") String repositorySlug,
      @QueryMap Map<String, String> requestParams);

  @GET("/builds/artifacts/{buildNumber}/{master}/{job}")
  List<Artifact> getArtifacts(
      @Path("buildNumber") Integer buildNumber,
      @Query("propertyFile") String propertyFile,
      @Path("master") String master,
      @Path(value = "job", encode = false) String job);

  @POST("/gcb/builds/create/{account}")
  GoogleCloudBuild createGoogleCloudBuild(
      @Path("account") String account, @Body Map<String, Object> job);

  @GET("/gcb/builds/{account}/{buildId}")
  GoogleCloudBuild getGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/gcb/builds/{account}/{buildId}/artifacts")
  List<Artifact> getGoogleCloudBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("/gcb/triggers/{account}/{triggerId}/run")
  GoogleCloudBuild runGoogleCloudBuildTrigger(
      @Path("account") String account,
      @Path("triggerId") String triggerId,
      @Body GoogleCloudBuildRepoSource repoSource);

  @POST("/codebuild/builds/start/{account}")
  AwsCodeBuildExecution startAwsCodeBuild(
      @Path("account") String account, @Body Map<String, Object> requestInput);

  @GET("/codebuild/builds/{account}/{buildId}")
  AwsCodeBuildExecution getAwsCodeBuildExecution(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("/codebuild/builds/stop/{account}/{buildId}")
  AwsCodeBuildExecution stopAwsCodeBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/delivery-config/manifest")
  Map<String, Object> getDeliveryConfigManifest(
      @Query("scmType") String repoType,
      @Query("project") String projectKey,
      @Query("repository") String repositorySlug,
      @Query("directory") @Nullable String directory,
      @Query("manifest") @Nullable String manifest,
      @Query("ref") @Nullable String ref);
}
