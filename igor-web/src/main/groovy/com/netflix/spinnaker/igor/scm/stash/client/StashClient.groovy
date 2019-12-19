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

package com.netflix.spinnaker.igor.scm.stash.client

import com.netflix.spinnaker.igor.scm.stash.client.model.CompareCommitsResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryListingResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query
import retrofit.http.QueryMap

/**
 * Interface for interacting with a Stash REST API
 * https://developer.atlassian.com/static/rest/stash/3.9.1/stash-rest.html
 */
interface StashClient {
    @GET('/rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/compare/commits')
    CompareCommitsResponse getCompareCommits(
        @Path('projectKey') String projectKey,
        @Path('repositorySlug') String repositorySlug,
        @QueryMap Map queryMap)

  // TODO: pagination support
  @GET('/rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/browse/{dirPath}')
  DirectoryListingResponse listDirectory(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @Path(value = 'dirPath', encode = false) String dirPath,
    @Query('at') String at
  )

  // TODO: pagination support
  @GET('/rest/api/1.0/projects/{projectKey}/repos/{repositorySlug}/browse/{filePath}')
  TextLinesResponse getTextFileContents(
    @Path('projectKey') String projectKey,
    @Path('repositorySlug') String repositorySlug,
    @Path(value = 'filePath', encode = false) String filePath,
    @Query('at') String at
  )
}

