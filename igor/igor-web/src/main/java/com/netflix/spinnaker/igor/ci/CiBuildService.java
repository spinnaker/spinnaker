/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.ci;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import java.util.List;
import java.util.Map;

/**
 * Interface to be implemented by ci build service providers that supports a way of getting builds
 * by supplying a project key, repo name and an optional completionStatus. [Future] By implementing
 * this service, deck will pick up the builds and will be able to show them in the ui.
 */
public interface CiBuildService {

  /**
   * Get the builds given project key, repo slug, and status. The data returned from a
   * CiBuildService powers the CI view in the UI.
   *
   * @param projectKey the project key
   * @param repoSlug the repository name
   * @param buildNumber the build number
   * @param commitId the commit id
   * @param completionStatus filter builds based on status
   * @return a list of builds
   */
  List<GenericBuild> getBuilds(
      String projectKey,
      String repoSlug,
      String buildNumber,
      String commitId,
      String completionStatus);

  /**
   * Get the build log by providing the build id. The data returned will be used in the CI view, to
   * present the build's log.
   *
   * @param buildId the build id
   * @return a map with key "log" which represent the build log
   */
  Map<String, Object> getBuildOutput(String buildId);
}
