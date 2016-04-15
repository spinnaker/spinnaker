/*
 * Copyright 2016 Schibsted ASA.
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

@JsonInclude(JsonInclude.Include.NON_NULL)
class GenericBuild {
    boolean building
    String fullDisplayName
    int number
    Integer duration
    String timestamp
    Result result
    List<GenericArtifact> artifacts;
    String url
    @JsonProperty("scm")
    List<GenericGitRevision> genericGitRevisions

    GenericBuild(boolean building, int number) {
        this.building = building
        this.number = number
    }

    GenericBuild(boolean building, int number, int duration, Result result, String name, String url) {
        this(building, number)
        this.duration = duration
        this.result = result
        this.fullDisplayName = "${name} #${number}"
        this.url = url
    }

    GenericBuild(boolean building, int number, int duration, Result result, String name, String url, String timestamp, String fullDisplayName) {
        this(building, number, duration, result, name, url)
        this.timestamp = timestamp
        this.fullDisplayName = fullDisplayName
    }

}
