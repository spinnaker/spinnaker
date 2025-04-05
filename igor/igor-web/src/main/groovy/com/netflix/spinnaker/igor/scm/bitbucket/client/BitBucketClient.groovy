/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm.bitbucket.client

import com.netflix.spinnaker.igor.scm.bitbucket.client.model.CompareCommitsResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap

/**
 * Interface for interacting with a BitBucket Cloud REST API
 * https://developer.atlassian.com/bitbucket/api/2/reference/resource/repositories/%7Busername%7D/%7Brepo_slug%7D/commits
 */
interface BitBucketClient {
  @GET('2.0/repositories/{projectKey}/{repositorySlug}/commits')
  Call<CompareCommitsResponse> getCompareCommits(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @QueryMap Map<String,String> queryMap)
}

