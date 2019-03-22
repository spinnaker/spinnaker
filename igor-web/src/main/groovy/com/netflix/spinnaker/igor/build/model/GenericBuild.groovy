/*
 * Copyright 2016 Schibsted ASA.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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

package com.netflix.spinnaker.igor.build.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.igor.jenkins.client.model.TestResults

@JsonInclude(JsonInclude.Include.NON_NULL)
class GenericBuild {
    boolean building
    String fullDisplayName
    String name
    int number
    Integer duration

    /**
     * String representation of time in nanoseconds since Unix epoch
     */
    String timestamp

    Result result
    List<GenericArtifact> artifacts
    List<TestResults> testResults
    String url
    String id
    @JsonProperty("scm")
    List<GenericGitRevision> genericGitRevisions
    Map<String, ?> properties
}
