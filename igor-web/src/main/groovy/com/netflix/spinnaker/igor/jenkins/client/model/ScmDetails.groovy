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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import groovy.transform.CompileStatic

import javax.xml.bind.annotation.XmlElement
import java.util.stream.Collectors

/**
 * Represents git details
 *
 * The serialization of these details in the Jenkins build XML changed in version 4.0.0 of the jenkins-git plugin.
 *
 * Prior to 4.0.0, the format was:
 * <action _class="hudson.plugins.git.util.BuildData">
 *   <lastBuiltRevision>
 *     <branch>
 *       <SHA1>943a702d06f34599aee1f8da8ef9f7296031d699</SHA1>
 *       <name>refs/remotes/origin/master</name>
 *     </branch>
 *   </lastBuiltRevision>
 *   <remoteUrl>some-url</remoteUrl>
 * </action>
 *
 * As of version 4.0.0, the format is:
 * <action _class="hudson.plugins.git.util.BuildDetails">
 *   <build>
 *     <revision>
 *       <branch>
 *         <SHA1>943a702d06f34599aee1f8da8ef9f7296031d699</SHA1>
 *         <name>refs/remotes/origin/master</name>
 *       </branch>
 *     </revision>
 *     <remoteUrl>some-url</remoteUrl>
 *   </build>
 * </action>
 *
 * The code in this module should remain compatible with both formats to ensure that SCM info is populated in Spinnaker
 * regardless of which version of the jenkins-git plugin is being used.
 */
@CompileStatic
class ScmDetails {
    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "action")
    ArrayList<Action> actions

    List<GenericGitRevision> genericGitRevisions() {
        List<GenericGitRevision> genericGitRevisions = new ArrayList<GenericGitRevision>()

        if (actions == null) {
            return null
        }

        for (Action action : actions) {
            Revision revision = action?.lastBuiltRevision ?: action?.build?.revision
            if (revision?.branch?.name) {
                genericGitRevisions.addAll(revision.branch.collect() { Branch branch ->
                  GenericGitRevision.builder()
                    .name(branch.getName())
                    .branch(branch.name.split('/').last())
                    .sha1(branch.sha1)
                    .remoteUrl(action.remoteUrl)
                    .build()
                })
            }
        }

        // If the same revision appears in both the old and the new location in the XML, we only want to return it once
        return genericGitRevisions.stream().distinct().collect(Collectors.toList())
    }
}

class Action {
    @XmlElement(required = false)
    Revision lastBuiltRevision

    @XmlElement(required = false)
    ScmBuild build

    @XmlElement(required = false)
    String remoteUrl
}

class ScmBuild {
    @XmlElement(required = false)
    Revision revision
}

class Revision {
    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "branch")
    List<Branch> branch
}

class Branch {
    @XmlElement(required = false)
    String name

    @XmlElement(required = false, name = "SHA1")
    String sha1
}
