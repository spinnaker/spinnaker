/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.services;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface IgorService {
  @GET("builds/status/{buildNumber}/{master}/{job}")
  Call<Map<String, Object>> getBuild(
      @Path("buildNumber") Integer buildNumber,
      @Path("master") String master,
      @Path(value = "job", encoded = true) String job);

  @GET("builds/status/{buildNumber}/{master}")
  Call<Map<String, Object>> getBuildStatusWithJobQueryParameter(
      @NotNull @Path("buildNumber") Integer buildNumber,
      @NotNull @Path("master") String master,
      @NotNull @Query(value = "job") String job);

  @GET("builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Call<Map<String, Object>> getPropertyFile(
      @Path("buildNumber") Integer buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Path(value = "job", encoded = true) String job);

  @GET("builds/properties/{buildNumber}/{fileName}/{master}")
  Call<Map<String, Object>> getPropertyFileWithJobQueryParameter(
      @Path("buildNumber") Integer buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Query(value = "job") String job);

  @GET("builds/artifacts/{buildNumber}/{master}/{job}")
  Call<List<Artifact>> getArtifacts(
      @Path("buildNumber") Integer buildNumber,
      @Path("master") String master,
      @Path(value = "job", encoded = true) String job,
      @Query("propertyFile") String propertyFile);

  @GET("builds/artifacts/{buildNumber}/{master}")
  Call<List<Artifact>> getArtifactsWithJobQueryParameter(
      @Path("buildNumber") Integer buildNumber,
      @Path("master") String master,
      @Query(value = "job") String job,
      @Query("propertyFile") String propertyFile);

  @GET("artifacts/{provider}/{packageName}")
  Call<List<String>> getVersions(
      @Path("provider") String provider, @Path("packageName") String packageName);

  @GET("artifacts/{provider}/{packageName}/{version}")
  Call<Artifact> getArtifactByVersion(
      @Path("provider") String provider,
      @Path("packageName") String packageName,
      @Path("version") String version);

  @PUT("gcb/builds/{account}/{buildId}")
  Call<ResponseBody> updateBuildStatus(
      @Path("account") String account,
      @Path("buildId") String buildId,
      @Query("status") String status,
      @Body RequestBody build);

  @PUT("gcb/artifacts/extract/{account}")
  Call<List<Artifact>> extractGoogleCloudBuildArtifacts(
      @Path("account") String account, @Body RequestBody build);
}
