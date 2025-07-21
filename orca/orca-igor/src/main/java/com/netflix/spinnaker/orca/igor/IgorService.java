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
import retrofit.client.Response;
import retrofit.http.*;

public interface IgorService {
<<<<<<< HEAD
  @PUT("/masters/{name}/jobs/{jobName}")
  Response build(
=======
  @PUT("masters/{name}/jobs/{jobName}")
  Call<ResponseBody> build(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("name") String master,
      @Path(encode = false, value = "jobName") String jobName,
      @QueryMap Map<String, String> queryParams,
      @Body String startTime);

<<<<<<< HEAD
  @PUT("/masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}")
  String stop(
=======
  @PUT("masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}")
  Call<String> stop(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("name") String master,
      @Path(encode = false, value = "jobName") String jobName,
      @Path(encode = false, value = "queuedBuild") String queuedBuild,
      @Path(encode = false, value = "buildNumber") Long buildNumber,
      @Body String ignored);

<<<<<<< HEAD
  @PUT("/masters/{name}/jobs/stop/{queuedBuild}/{buildNumber}")
  String stopWithJobNameAsQueryParameter(
=======
  @PUT("masters/{name}/jobs/stop/{queuedBuild}/{buildNumber}")
  Call<String> stopWithJobNameAsQueryParameter(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("name") String master,
      @Query(value = "jobName") String jobName,
      @Path(encode = false, value = "queuedBuild") String queuedBuild,
      @Path(encode = false, value = "buildNumber") Long buildNumber,
      @Body String ignored);

<<<<<<< HEAD
  @PATCH("/masters/{name}/jobs/{jobName}/update/{buildNumber}")
  Response update(
=======
  @PATCH("masters/{name}/jobs/{jobName}/update/{buildNumber}")
  Call<ResponseBody> update(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("name") String master,
      @Path(encode = false, value = "jobName") String jobName,
      @Path(encode = false, value = "buildNumber") Long buildNumber,
      @Body UpdatedBuild updatedBuild);

<<<<<<< HEAD
  @GET("/builds/queue/{master}/{item}")
  Map queuedBuild(@Path("master") String master, @Path("item") String item);

  @GET("/builds/status/{buildNumber}/{master}/{job}")
  Map<String, Object> getBuild(
=======
  @GET("builds/queue/{master}/{item}")
  Call<Map> queuedBuild(@Path("master") String master, @Path("item") String item);

  @GET("builds/status/{buildNumber}/{master}/{job}")
  Call<Map<String, Object>> getBuild(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("buildNumber") Long buildNumber,
      @Path("master") String master,
      @Path(encode = false, value = "job") String job);

<<<<<<< HEAD
  @GET("/builds/status/{buildNumber}/{master}")
  Map<String, Object> getBuildWithJobAsQueryParam(
=======
  @GET("builds/status/{buildNumber}/{master}")
  Call<Map<String, Object>> getBuildWithJobAsQueryParam(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("buildNumber") Long buildNumber,
      @Path("master") String master,
      @Query(encodeValue = false, value = "job") String job);

<<<<<<< HEAD
  @GET("/builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Map<String, Object> getPropertyFile(
=======
  @GET("builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Call<Map<String, Object>> getPropertyFile(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("buildNumber") Long buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Path(encode = false, value = "job") String job);

<<<<<<< HEAD
  @GET("/builds/properties/{buildNumber}/{fileName}/{master}")
  Map<String, Object> getPropertyFileWithJobAsQueryParam(
=======
  @GET("builds/properties/{buildNumber}/{fileName}/{master}")
  Call<Map<String, Object>> getPropertyFileWithJobAsQueryParam(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("buildNumber") Long buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Query(encodeValue = false, value = "job") String job);

<<<<<<< HEAD
  @GET("/{repoType}/{projectKey}/{repositorySlug}/compareCommits")
  List compareCommits(
=======
  @GET("{repoType}/{projectKey}/{repositorySlug}/compareCommits")
  Call<List> compareCommits(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("repoType") String repoType,
      @Path("projectKey") String projectKey,
      @Path("repositorySlug") String repositorySlug,
      @QueryMap Map<String, String> requestParams);

<<<<<<< HEAD
  @GET("/builds/artifacts/{buildNumber}/{master}/{job}")
  List<Artifact> getArtifacts(
=======
  @GET("builds/artifacts/{buildNumber}/{master}/{job}")
  Call<List<Artifact>> getArtifacts(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("buildNumber") Long buildNumber,
      @Query("propertyFile") String propertyFile,
      @Path("master") String master,
      @Path(value = "job", encode = false) String job);

<<<<<<< HEAD
  @GET("/builds/artifacts/{buildNumber}/{master}")
  List<Artifact> getArtifactsWithJobAsQueryParam(
=======
  @GET("builds/artifacts/{buildNumber}/{master}")
  Call<List<Artifact>> getArtifactsWithJobAsQueryParam(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("buildNumber") Long buildNumber,
      @Query("propertyFile") String propertyFile,
      @Path("master") String master,
      @Query(value = "job", encodeValue = false) String job);

<<<<<<< HEAD
  @POST("/gcb/builds/create/{account}")
  GoogleCloudBuild createGoogleCloudBuild(
      @Path("account") String account, @Body Map<String, Object> job);

  @POST("/gcb/builds/stop/{account}/{buildId}")
  GoogleCloudBuild stopGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/gcb/builds/{account}/{buildId}")
  GoogleCloudBuild getGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/gcb/builds/{account}/{buildId}/artifacts")
  List<Artifact> getGoogleCloudBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("/gcb/triggers/{account}/{triggerId}/run")
  GoogleCloudBuild runGoogleCloudBuildTrigger(
=======
  @POST("gcb/builds/create/{account}")
  Call<GoogleCloudBuild> createGoogleCloudBuild(
      @Path("account") String account, @Body Map<String, Object> job);

  @POST("gcb/builds/stop/{account}/{buildId}")
  Call<GoogleCloudBuild> stopGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("gcb/builds/{account}/{buildId}")
  Call<GoogleCloudBuild> getGoogleCloudBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("gcb/builds/{account}/{buildId}/artifacts")
  Call<List<Artifact>> getGoogleCloudBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("gcb/triggers/{account}/{triggerId}/run")
  Call<GoogleCloudBuild> runGoogleCloudBuildTrigger(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("triggerId") String triggerId,
      @Body GoogleCloudBuildRepoSource repoSource);

<<<<<<< HEAD
  @POST("/codebuild/builds/start/{account}")
  AwsCodeBuildExecution startAwsCodeBuild(
      @Path("account") String account, @Body Map<String, Object> requestInput);

  @GET("/codebuild/builds/{account}/{buildId}")
  AwsCodeBuildExecution getAwsCodeBuildExecution(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/codebuild/builds/artifacts/{account}/{buildId}")
  List<Artifact> getAwsCodeBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("/codebuild/builds/stop/{account}/{buildId}")
  AwsCodeBuildExecution stopAwsCodeBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("/delivery-config/manifest")
  Map<String, Object> getDeliveryConfigManifest(
=======
  @POST("codebuild/builds/start/{account}")
  Call<AwsCodeBuildExecution> startAwsCodeBuild(
      @Path("account") String account, @Body Map<String, Object> requestInput);

  @GET("codebuild/builds/{account}/{buildId}")
  Call<AwsCodeBuildExecution> getAwsCodeBuildExecution(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("codebuild/builds/artifacts/{account}/{buildId}")
  Call<List<Artifact>> getAwsCodeBuildArtifacts(
      @Path("account") String account, @Path("buildId") String buildId);

  @POST("codebuild/builds/stop/{account}/{buildId}")
  Call<AwsCodeBuildExecution> stopAwsCodeBuild(
      @Path("account") String account, @Path("buildId") String buildId);

  @GET("delivery-config/manifest")
  Call<Map<String, Object>> getDeliveryConfigManifest(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
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
