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

package com.netflix.spinnaker.igor.scm.github.client

import com.netflix.spinnaker.igor.scm.github.client.model.Commit
import com.netflix.spinnaker.igor.scm.github.client.model.CompareCommitsResponse
import com.netflix.spinnaker.igor.scm.github.client.model.GetRepositoryContentResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface for interacting with a GitHub REST API
 */
interface GitHubClient {
  @GET('repos/{projectKey}/{repositorySlug}/compare/{fromCommit}...{toCommit}')
  Call<CompareCommitsResponse> getCompareCommits(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @Path('fromCommit') String fromCommit,
    @Path('toCommit') String toCommit)

  @GET('repos/{projectKey}/{repositorySlug}/contents/{path}')
  Call<GetRepositoryContentResponse> getFileContent(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @Path(value = 'path', encoded = true) String path,
    @Query('ref') String ref
  )

  @GET('repos/{projectKey}/{repositorySlug}/contents/{path}')
  Call<List<GetRepositoryContentResponse>> listDirectory(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @Path(value = 'path', encoded = true) String path,
    @Query('ref') String ref
  )

  @GET('repos/{projectKey}/{repositorySlug}/git/commits/{sha}')
  Call<Commit> commitInfo(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @Path('sha') String sha
  )
}

