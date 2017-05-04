/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client

import com.netflix.spinnaker.igor.travis.client.model.AccessToken
import com.netflix.spinnaker.igor.travis.client.model.Accounts
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Builds
import com.netflix.spinnaker.igor.travis.client.model.EmptyObject
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth
import com.netflix.spinnaker.igor.travis.client.model.Jobs
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.RepoWrapper
import com.netflix.spinnaker.igor.travis.client.model.Repos
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Builds
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.EncodedPath
import retrofit.http.GET
import retrofit.http.Header
import retrofit.http.Headers
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query
import retrofit.http.Streaming

interface TravisClient {

    @POST('/auth/github')
    AccessToken accessToken(@Body GithubAuth gitHubAuth)

    @GET('/accounts')
    Accounts accounts(@Header("Authorization") String accessToken)

    @GET('/builds')
    Builds builds(@Header("Authorization") String accessToken)

    @GET('/builds')
    Builds builds(@Header("Authorization") String accessToken, @Query('repository_id') int repositoryId)

    @GET('/builds')
    Builds builds(@Header("Authorization") String accessToken, @Query('slug') String repoSlug)

    @GET('/builds/{build_id}')
    Build build(@Header("Authorization") String accessToken, @Path('build_id') int buildId)

    @GET('/builds')
    Build build(@Header("Authorization") String accessToken, @Query('repository_id') int repositoryId, @Query('number') int buildNumber)

    @GET('/builds')
    Builds builds(@Header("Authorization") String accessToken, @Query('slug') String repoSlug, @Query('number') int buildNumber)

    @GET('/repos')
    Repos repos(@Header("Authorization") String accessToken)

    @GET('/repos')
    Repos repos(@Header("Authorization") String accessToken , @Query('member') String login)

    @GET('/repos/{repositoryId}')
    Repo repo(@Header("Authorization") String accessToken, @Path('repositoryId') int repositoryId)

    @GET('/repos/{repo_slug}')
    RepoWrapper repoWrapper(@Header("Authorization") String accessToken, @EncodedPath('repo_slug') String repoSlug)

    @POST('/repo/{repoSlug}/requests')
    @Headers("Travis-API-Version: 3")
    TriggerResponse triggerBuild(@Header("Authorization") String accessToken, @Path('repoSlug') String repoSlug, @Body RepoRequest repoRequest)

    @POST('/users/sync')
    Response usersSync(@Header("Authorization") String accessToken, @Body EmptyObject emptyObject)

    @GET('/jobs/{job_id}')
    Jobs jobs(@Header("Authorization") String accessToken , @Path('job_id') int jobId)

    @Streaming
    @Headers("Accept: text/plain")
    @GET('/logs/{logId}')
    Response log(@Header("Authorization") String accessToken , @Path('logId') int logId)

    @Streaming
    @Headers([
        "Travis-API-Version: 3",
        "Accept: text/plain"
    ])
    @GET('/job/{jobId}/log')
    Response jobLog(@Header("Authorization") String accessToken , @Path('jobId') int jobId)

    @GET('/repo/{repository_id}/builds')
    @Headers("Travis-API-Version: 3")
    V3Builds builds(@Header("Authorization") String accessToken, @Path('repository_id') int repositoryId, @Query('limit') int limit)

}
