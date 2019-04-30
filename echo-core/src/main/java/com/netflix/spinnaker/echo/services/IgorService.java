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
import retrofit.client.Response;
import retrofit.http.*;
import retrofit.mime.TypedInput;

public interface IgorService {
  @GET("/builds/status/{buildNumber}/{master}/{job}")
  Map<String, Object> getBuild(
      @Path("buildNumber") Integer buildNumber,
      @Path("master") String master,
      @Path(value = "job", encode = false) String job);

  @GET("/builds/properties/{buildNumber}/{fileName}/{master}/{job}")
  Map<String, Object> getPropertyFile(
      @Path("buildNumber") Integer buildNumber,
      @Path("fileName") String fileName,
      @Path("master") String master,
      @Path(value = "job", encode = false) String job);

  @GET("/builds/artifacts/{buildNumber}/{master}/{job}")
  List<Artifact> getArtifacts(
      @Path("buildNumber") Integer buildNumber,
      @Query("propertyFile") String propertyFile,
      @Path("master") String master,
      @Path(value = "job", encode = false) String job);

  @GET("/artifacts/{provider}/{packageName}")
  List<String> getVersions(
      @Path("provider") String provider, @Path("packageName") String packageName);

  @GET("/artifacts/{provider}/{packageName}/{version}")
  Artifact getArtifactByVersion(
      @Path("provider") String provider,
      @Path("packageName") String packageName,
      @Path("version") String version);

  @PUT("/gcb/builds/{account}/{buildId}")
  Response updateBuildStatus(
      @Path("account") String account,
      @Path("buildId") String buildId,
      @Query("status") String status,
      @Body TypedInput build);

  @PUT("/gcb/artifacts/extract/{account}")
  List<Artifact> extractGoogleCloudBuildArtifacts(
      @Path("account") String account, @Body TypedInput build);
}
