/*
 * Copyright 2017 bol.com
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

package com.netflix.spinnaker.igor.scm.gitlab.client;

import com.netflix.spinnaker.igor.scm.gitlab.client.model.CompareCommitsResponse;
import java.util.Map;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.QueryMap;

/** Interface for interacting with a GitLab REST API https://docs.gitlab.com/ce/api/README.html */
public interface GitLabClient {

  /** https://docs.gitlab.com/ce/api/repositories.html#compare-branches-tags-or-commits */
  @GET("/api/v4/projects/{projectKey}%2F{repositorySlug}/repository/compare")
  CompareCommitsResponse getCompareCommits(
      @Path("projectKey") String projectKey,
      @Path("repositorySlug") String repositorySlug,
      @QueryMap Map queryMap);
}
