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

package com.netflix.spinnaker.igor.travis.client.logparser

import com.netflix.spinnaker.igor.build.model.GenericArtifact
import spock.lang.Specification

class ArtifactParserTest extends Specification {
    def "get single artifactory deb from log"() {
        String buildLog = "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created"
        when:
        List<GenericArtifact> artifacts = ArtifactParser.getArtifactsFromLog(buildLog)

        then:
        artifacts.first().fileName == "some-package_0.0.7_amd64.deb"
    }

    def "get single artifactory rpm from log"() {
        String buildLog = "[Thread 0] Uploading artifact: https://foo.host/artifactory/yum-local/some/nice/path/some-package-0.0.4.1-1.x86_64.rpm\n" +
            "[Thread 0] Artifactory response: 201 Created\n" +
            "Uploaded 1 artifacts to Artifactory."
        when:
        List<GenericArtifact> artifacts = ArtifactParser.getArtifactsFromLog(buildLog)

        then:
        artifacts.first().fileName == "some-package-0.0.4.1-1.x86_64.rpm"
    }

    def "get multiple artifactory deb from log"() {
        String buildLog = "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created\n" +
            "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/other/path/some-other-package_1.3.3.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created"
        when:
        List<GenericArtifact> artifacts = ArtifactParser.getArtifactsFromLog(buildLog)

        then:
        artifacts.first().fileName == "some-package_0.0.7_amd64.deb"
        artifacts.last().fileName == "some-other-package_1.3.3.7_amd64.deb"
    }
}
