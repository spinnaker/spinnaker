/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.errors.v1.config;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigCoordinates;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigProblem;

/**
 * This is meant for requests that Halyard cannot figure out how to handle.
 * For example: Asking to load an account that isn't in your config.
 */
public class IllegalRequestException extends HalconfigException {
  public IllegalRequestException(String message, String remediation) {
    HalconfigProblem problem = new HalconfigProblem(HalconfigProblem.Severity.FATAL, message, remediation);
    getProblems().add(problem);
  }

  public IllegalRequestException(HalconfigCoordinates coordinates, String message, String remediation) {
    HalconfigProblem problem = new HalconfigProblem(HalconfigProblem.Severity.FATAL, coordinates, message, remediation);
    getProblems().add(problem);
  }
}
