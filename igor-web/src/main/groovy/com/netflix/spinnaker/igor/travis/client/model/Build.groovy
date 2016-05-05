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

package com.netflix.spinnaker.igor.travis.client.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Root

@Default
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
@Root(name = 'builds')
class Build {
    @SerializedName("commit_id")
    int commitId
    int duration
    int id
    @SerializedName("repository_id")
    int repositoryId
    int number
    String state
    @SerializedName("finished_at")
    Date finishedAt
    @SerializedName("pull_request")
    Boolean pullRequest
    @JsonProperty(value = "job_ids")
    List <Integer> job_ids

    long timestamp() {
        return finishedAt.getTime()
    }
}
