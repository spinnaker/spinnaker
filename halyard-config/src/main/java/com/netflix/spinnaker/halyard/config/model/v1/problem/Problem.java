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

package com.netflix.spinnaker.halyard.config.model.v1.problem;

import com.netflix.spinnaker.halyard.config.model.v1.node.NodeCoordinates;
import lombok.Getter;

/**
 * This represents a single "problem" with the currently loaded/modified halconfig.
 */
public class Problem {
  public enum Severity {
    /**
     * Inicates no problem at all. This exists as a point of comparison against the greater severity levels, and
     * may not be used to instantiate a problem.
     */
    NONE,

    /**
     * Indicates the deployment of Spinnaker is going against our preferred/recommended practices.
     * For example: using an unauthenticated docker registry.
     */
    WARNING,

    /**
     * Indicates the deployment of Spinnaker will fail as-is (but the request can be performed).
     * For example: using an incorrect password in your docker registry.
     */
    ERROR,

    /**
     * Indicates this request cannot hope to be performed
     * For example: asking to update an account that doesn't exist.
     */
    FATAL,
  }

  /**
   * The location of the problem in the config.
   */
  @Getter
  final private NodeCoordinates coordinates;

  /**
   * A human-readable message describing the problem.
   */
  @Getter
  final private String message;

  /**
   * An optional human-readable message describing how to fix the problem.
   */
  @Getter
  final private String remediation;

  /**
   * Indicates if this will cause the deployment to fail or not.
   */
  @Getter
  final private Severity severity;

  public Problem(Severity severity, NodeCoordinates coordinates, String message, String remediation) {
    if (severity == Severity.NONE) {
      throw new RuntimeException("A halconfig problem may not be intialized with \"NONE\" severity");
    }
    this.severity = severity;
    this.coordinates = coordinates;
    this.message = message;
    this.remediation = remediation;
  }
}
