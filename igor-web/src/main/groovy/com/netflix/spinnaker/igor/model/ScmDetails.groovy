/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins.client.model

import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * Represents git details
 */
@Default
@CompileStatic
@Root(strict = false)
class ScmDetails {
    @ElementList(inline = true, required = false, name = "action")
    ArrayList<Action> actions


    List<GenericGitRevision> genericGitRevisions() {
        List<GenericGitRevision> genericGitRevisions = new ArrayList<GenericGitRevision>()

        if (actions == null) {
            return null
        }

        for (Action action : actions) {
            if (action?.lastBuiltRevision?.branch?.name) {
                genericGitRevisions.addAll(action.lastBuiltRevision.branch.collect() { Branch branch ->
                    new GenericGitRevision(branch.name, branch.name.split('/').last(), branch.sha1, action.remoteUrl)
                })
            }
        }

        return genericGitRevisions
    }
}

@Root(strict = false)
class Action {
    @Element(required = false)
    LastBuiltRevision lastBuiltRevision

    @Element(required = false)
    String remoteUrl
}

@Root(strict = false)
class LastBuiltRevision {
    @ElementList(inline = true, name = "branch")
    List<Branch> branch
}

@Root(strict = false)
class Branch {
    @Element(required = false)
    String name

    @Element(required = false, name = "SHA1")
    String sha1
}
