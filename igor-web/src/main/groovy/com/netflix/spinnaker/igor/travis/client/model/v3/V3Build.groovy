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

package com.netflix.spinnaker.igor.travis.client.model.v3

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.travis.client.model.Config
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Root

@Default
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
@Root(name = 'builds')
class V3Build {
    V3Branch branch
    @SerializedName("commit_id")
    int commitId
    V3Commit commit
    int duration
    @SerializedName("event_type")
    String eventType
    int id
    V3Repository repository
    @SerializedName("repository_id")
    int repositoryId
    int number
    TravisBuildState state
    @SerializedName("finished_at")
    Date finishedAt
    @JsonProperty(value = "job_ids")
    List <Integer> job_ids
    Config config

    long timestamp() {
        return finishedAt.getTime()
    }

    String branchedRepoSlug() {
        if(commit?.isPullRequest()) {
            return "${repository.slug}/pull_request_${branch.name}"
        }

        if(commit?.isTag()) {
            return "${repository.slug}/tags"
        }

        return "${repository.slug}/${branch.name}"

    }

    GenericGitRevision genericGitRevision() {
        return new GenericGitRevision(branch.name, branch.name, commit.sha)
    }

    boolean spinnakerTriggered(){
        return (eventType == "api" && commit.message == "Triggered from spinnaker")
    }
}
