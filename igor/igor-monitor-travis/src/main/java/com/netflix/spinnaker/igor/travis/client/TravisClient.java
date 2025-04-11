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
import com.netflix.spinnaker.igor.travis.client.model.Builds;
import com.netflix.spinnaker.igor.travis.client.model.EmptyObject;
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth;
import com.netflix.spinnaker.igor.travis.client.model.v3.RepoRequest;
import com.netflix.spinnaker.igor.travis.client.model.v3.Request;
import com.netflix.spinnaker.igor.travis.client.model.v3.Root;
import com.netflix.spinnaker.igor.travis.client.model.v3.TriggerResponse;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Jobs;
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Log;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TravisClient {
  /**
   * Root endpoint (<a
   * href="https://developer.travis-ci.com/resource/home">developer.travis-ci.com/resource/home</a>),
   * describes the API and its capabilities
   *
   * @return The root object, describing the API
   */
  @GET("v3/")
  @Headers("Travis-API-Version: 3")
  public Call<Root> getRoot();

  @POST("auth/github")
  public abstract Call<AccessToken> accessToken(@Body GithubAuth gitHubAuth);

  @GET("builds")
  public abstract Call<Builds> builds(
      @Header("Authorization") String accessToken,
      @Query("slug") String repoSlug,
      @Query("number") long buildNumber);

  @POST("repo/{repoSlug}/requests")
  @Headers("Travis-API-Version: 3")
  public abstract Call<TriggerResponse> triggerBuild(
      @Header("Authorization") String accessToken,
      @Path("repoSlug") String repoSlug,
      @Body RepoRequest repoRequest);

  @POST("users/sync")
  public abstract Call<ResponseBody> usersSync(
      @Header("Authorization") String accessToken, @Body EmptyObject empty);

  @Headers({"Travis-API-Version: 3", "Accept: text/plain"})
  @GET("job/{jobId}/log")
  public abstract Call<V3Log> jobLog(
      @Header("Authorization") String accessToken, @Path("jobId") int jobId);

  @GET("build/{build_id}")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Build> v3build(
      @Header("Authorization") String accessToken,
      @Path("build_id") long buildId,
      @Query("include") String include);

  @GET("repo/{repository_id}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Builds> builds(
      @Header("Authorization") String accessToken,
      @Path("repository_id") long repositoryId,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Builds> v3builds(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Builds> v3builds(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("branch.name") String branchName,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Builds> v3builds(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("branch.name") String branchName,
      @Query("event_type") String eventType,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("repo/{repository_slug}/builds")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Builds> v3buildsByEventType(
      @Header("Authorization") String accessToken,
      @Path("repository_slug") String repositorySlug,
      @Query("event_type") String EventType,
      @Query("limit") int limit,
      @Query("include") String include);

  @GET("repo/{repository_id}/request/{request_id}")
  @Headers("Travis-API-Version: 3")
  public abstract Call<Request> request(
      @Header("Authorization") String accessToken,
      @Path("repository_id") long repositoryId,
      @Path("request_id") long requestId);

  @GET("jobs")
  @Headers("Travis-API-Version: 3")
  public abstract Call<V3Jobs> jobs(
      @Header("Authorization") String accessToken,
      @Query("state") String state,
      @Query("include") String include,
      @Query("limit") int limit,
      @Query("offset") int offset);
}
