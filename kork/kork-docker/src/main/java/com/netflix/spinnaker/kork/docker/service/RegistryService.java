/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.docker.service;

import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.docker.model.DockerBearerToken;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

@VisibleForTesting
public interface RegistryService {
  @GET("/{path}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> get(
      @Path(value = "path", encoded = true) String path,
      @Header("Authorization") String token,
      @Header("User-Agent") String agent,
      @QueryMap(encoded = true) Map<String, String> queryParams);

  @GET("/{path}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<DockerBearerToken> getToken(
      @Path(value = "path", encoded = true) String path,
      @Query("service") String service,
      @Query("scope") String scope,
      @Header("User-Agent") String agent);

  @GET("/{path}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<DockerBearerToken> getToken(
      @Path(value = "path", encoded = true) String path,
      @Query("service") String service,
      @Query("scope") String scope,
      @Header("Authorization") String basic,
      @Header("User-Agent") String agent);

  @GET("/{path}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> getManifest(
      @Path(value = "path", encoded = true) String path,
      @Header("Authorization") String auth,
      @Header("User-Agent") String userAgent);

  @GET("/v2/{name}/manifests/{reference}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> getManifest(
      @Path(value = "name", encoded = true) String name,
      @Path(value = "reference", encoded = true) String reference,
      @Header("Authorization") String token,
      @Header("User-Agent") String agent);

  @GET("/{path}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> downloadBlob(
      @Path(value = "path", encoded = true) String path,
      @Header("Authorization") String auth,
      @Header("User-Agent") String userAgent);

  @GET("/v2/{repository}/tags/list")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> getTags(
      @Path(value = "repository", encoded = true) String repository,
      @Header("Authorization") String token,
      @Header("User-Agent") String agent,
      @QueryMap(encoded = true) Map<String, String> queryParams);

  @GET("/v2/_catalog")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> getCatalog(
      @Header("Authorization") String token,
      @Header("User-Agent") String agent,
      @QueryMap(encoded = true) Map<String, String> queryParams);

  @GET("/v2/{name}/manifests/{reference}")
  @Headers({
    "Docker-Distribution-API-Version: registry/2.0",
    "Accept: application/vnd.docker.distribution.manifest.v2+json"
  })
  Call<ResponseBody> getSchemaV2Manifest(
      @Path(value = "name", encoded = true) String name,
      @Path(value = "reference", encoded = true) String reference,
      @Header("Authorization") String token,
      @Header("User-Agent") String agent);

  @GET("/v2/{repository}/blobs/{digest}")
  @Headers({"Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> getDigestContent(
      @Path(value = "repository", encoded = true) String repository,
      @Path(value = "digest", encoded = true) String digest,
      @Header("Authorization") String token,
      @Header("User-Agent") String agent);

  @GET("/v2/")
  @Headers({"User-Agent: Spinnaker-Clouddriver", "Docker-Distribution-API-Version: registry/2.0"})
  Call<ResponseBody> checkVersion(
      @Header("Authorization") String token, @Header("User-Agent") String agent);
}
