/*
 * Copyright 2019 Schibsted ASA.
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

package com.netflix.spinnaker.igor.service;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import java.util.List;
import java.util.Map;

/**
 * Interface representing a Build Service (CI) host, the permissions needed to access it, and build
 * operations on the host
 */
public interface BuildOperations extends BuildService {
  /**
   * Get a list of the Spinnaker representation of the Git commits relevant for the given build
   *
   * @param job The name of the job
   * @param buildNumber The build number
   * @return A list of git revisions relevant for the build
   */
  List<GenericGitRevision> getGenericGitRevisions(String job, int buildNumber);

  /**
   * Return all information of a given build
   *
   * @param job The name of the job
   * @param buildNumber The build number
   * @return A Spinnaker representation of a build
   */
  GenericBuild getGenericBuild(String job, int buildNumber);

  /**
   * Trigger a build of a given job on the build service host
   *
   * @param job The name of the job to be triggered
   * @param queryParameters A key-value map of parameters to be injected into the build
   * @return An id identifying the build; preferably the build number of the build
   */
  int triggerBuildWithParameters(String job, Map<String, String> queryParameters);

  /**
   * Returns all/relevant builds for the given job.
   *
   * @param job The name of the job
   * @return A list of builds
   */
  List<?> getBuilds(String job);
}
