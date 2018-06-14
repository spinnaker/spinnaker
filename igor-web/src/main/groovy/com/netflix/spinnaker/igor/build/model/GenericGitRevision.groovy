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

import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
class GenericGitRevision {
    String name
    String branch
    String sha1
    String committer
    String compareUrl
    String message
    Instant timestamp
    String remoteUrl

    GenericGitRevision(String name, String branch, String sha1) {
        this.name = name
        this.branch = branch
        this.sha1 = sha1
    }

    GenericGitRevision(String name, String branch, String sha1, String remoteUrl) {
        this.name = name
        this.branch = branch
        this.sha1 = sha1
        this.remoteUrl = remoteUrl
    }

    GenericGitRevision(String name, String branch, String sha1, String committer, String compareUrl, String message, Instant timestamp) {
        this(name, branch, sha1)
        this.committer = committer
        this.compareUrl = compareUrl
        this.message = message
        this.timestamp = timestamp
    }
}
