/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.core.problem.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

public class Problem {
  public enum Severity {
    /**
     * Indicates no problem at all. This exists as a point of comparison against the greater
     * severity levels, and may not be used to instantiate a problem.
     */
    NONE,

    /** Indicates no problem at all, just information that should be shared with the user. */
    INFO,

    /**
     * Indicates the deployment of Spinnaker is going against our preferred/recommended practices.
     * For example: using an unauthenticated docker registry.
     */
    WARNING,

    /**
     * Indicates the deployment of Spinnaker will fail as-is (but the request can be performed). For
     * example: using an incorrect password in your docker registry.
     */
    ERROR,

    /**
     * Indicates this request cannot hope to be performed. For example: asking to update an account
     * that doesn't exist.
     */
    FATAL,
  }

  /** A human-readable message describing the problem. */
  @Getter private final String message;

  /** An optional human-readable message describing how to fix the problem. */
  @Getter private final String remediation;

  /** An optional list of alternative entries. */
  @Getter private final List<String> options;

  /** Indicates if this will cause the deployment to fail or not. */
  @Getter private final Severity severity;

  /** Where this problem occured. */
  @Getter private final String location;

  @JsonCreator
  public Problem(
      @JsonProperty("message") String message,
      @JsonProperty("remediation") String remediation,
      @JsonProperty("options") List<String> options,
      @JsonProperty("severity") Severity severity,
      @JsonProperty("location") String location) {
    if (severity == Severity.NONE) {
      throw new RuntimeException(
          "A halconfig problem may not be intialized with \"NONE\" severity");
    }
    this.severity = severity;
    this.message = message;
    this.remediation = remediation;
    this.options = options;
    this.location = location;
  }
}
