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

import com.google.gson.annotations.SerializedName
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Root

@Default
@CompileStatic
@Root(name = 'commits')
class V3Commit {
    int id

    String sha

    String ref

    String message


    @SerializedName("compare_url")
    String compareUrl

    boolean isTag(){
        if (ref) {
            return ref.split("/")[1] == "tags"
        }
        return false
    }

    boolean isPullRequest(){
        if (ref) {
            return ref.split("/")[1] == "pull"
        }
        return false
    }
}
