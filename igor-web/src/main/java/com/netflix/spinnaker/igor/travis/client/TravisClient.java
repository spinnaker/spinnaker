/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client;

import com.netflix.spinnaker.igor.travis.client.model.AccessToken;
import com.netflix.spinnaker.igor.travis.client.model.Accounts;
import com.netflix.spinnaker.igor.travis.client.model.Builds;
import com.netflix.spinnaker.igor.travis.client.model.EmptyObject;
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth;
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest;
import com.netflix.spinnaker.igor.travis.client.model.Repos;
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse;
import com.netflix.spinnaker.igor.travis.client.model.v3.Request;
import com.netflix.spinnaker.igor.travis.client.model.v3.Root;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

public interface TravisClient {
  /**
   * Root endpoint (<a
   * href="https://developer.travis-ci.com/resource/home">developer.travis-ci.com/resource/home</a>),
   * describes the API and its capabilities
   *
   * @return The root object, describing the API
   */
  @GET("/v3/")
  @Headers("Travis-API-Version: 3")
  public Root getRoot();

  @POST("/auth/github")
  public abstract AccessToken accessToken(@Body GithubAuth gitHubAuth);

  @GET("/accounts")
  public abstract Accounts accounts(@Header("Authorization") String accessToken);

  @GET("/builds")
  public abstract Builds builds(
      @Header("Authorization") String accessToken,
      @Query("slug") String repoSlug,
      @Query("number") int buildNumber);

  @GET("/repos")
  public abstract Repos repos(
      @Header("Authorization") String accessToken,
      @Query("member") String login,
      @Query("active") boolean active,
      @Query("limit") int limit,
      @Query("offset") int offset);

  @POST("/repo/{repoSlug}/requests")
  @Headers("Travis-API-Version: 3")
  public abstract TriggerResponse triggerBuild(
      @Header("Authorization") String accessToken,
      @Path("repoSlug") String repoSlug,
      @Body RepoRequest repoRequest);

  @POST("/users/sync")
  public abstract Response usersSync(
      @Header("Authorization") String accessToken, @Body EmptyObject emptyObject);

  @Headers({"Travis-API-Version: 3", "Accept: text/plain"})
  @GET("/job/{jobId}/log")
  public abstract V3Log jobLog(
      @Header("Authorization") String accessToken, @Path("jobId") int jobId);

  @GET("/build/{build_id}")
  @Headers("Travis-API-Version: 3")
  public abstract V3Build v3build(
      @Header("Authorization") String accessToken,
      @Path("build_id") int buildId,
      @Query("include") String include);

  @GET("/repo/{repository_id}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract V3Builds builds(
      @Header("Authorization") String accessToken,
      @Path("repository_id") int repositoryId,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("/repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract V3Builds v3builds(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("/repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract V3Builds v3builds(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("branch.name") String branchName,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("/repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract V3Builds v3builds(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("branch.name") String branchName,
      @Query("event_type") String eventType,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("/repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract V3Builds v3buildsByEventType(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("event_type") String EventType,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("/repo/{repository_id}/request/{request_id}")
  @Headers("Travis-API-Version: 3")
  public abstract Request request(
      @Header("Authorization") String accessToken,
      @Path("repository_id") int repositoryId,
      @Path("request_id") int requestId);
}
