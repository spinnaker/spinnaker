/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.service;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Interface to be implemented by build service providers that supports a way of defining properties
 * in a build that will be injected into the pipeline context of pipeline executions triggered by
 * the given build
 */
public interface BuildProperties {
  /**
   * Get build properties defined in the given build
   *
   * @param job The name of the job
   * @param buildNumber The build number
   * @param fileName The file name containing the properties. Not all providers require this
   *     parameter.
   * @return A map of properties
   */
  Map<String, ?> getBuildProperties(String job, int buildNumber, @Nullable String fileName);
}
