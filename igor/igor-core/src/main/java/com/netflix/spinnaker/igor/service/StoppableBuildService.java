/*
 * Copyright 2025 Netflix, Inc.
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
package com.netflix.spinnaker.igor.service;

/**
 * Interface for CI build services that support stopping/cancelling running or queued builds.
 *
 * <p>Implement this interface alongside {@link BuildOperations} to enable Spinnaker's pipeline
 * cancel functionality for a given CI provider.
 */
public interface StoppableBuildService {

  /**
   * Stop a running build.
   *
   * @param jobName The name or identifier of the job (e.g., Jenkins job path, GitLab project ID)
   * @param buildNumber The build number to stop
   */
  void stopRunningBuild(String jobName, long buildNumber);

  /**
   * Stop a queued build that has not yet started execution.
   *
   * <p>Not all CI providers support this operation. The default implementation throws {@link
   * UnsupportedOperationException}.
   *
   * @param queuedId The identifier of the queued item
   */
  default void stopQueuedBuild(String queuedId) {
    throw new UnsupportedOperationException("Stopping queued builds is not supported");
  }
}
