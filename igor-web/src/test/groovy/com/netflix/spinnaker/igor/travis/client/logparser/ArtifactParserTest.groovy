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
import spock.lang.Unroll

class ArtifactParserTest extends Specification {

    /**
     * Covers projects pushing to artifactory with the Gradle plugin.
     */
    def "get multiple artifactory files from gradle log using custom regexes"() {
        String buildLog =
            ":publishPublishRpmPublicationToIvyRepository\n" +
            "Upload https://foo.host/artifactory/yum-local/theorg/theprj/some-package-1.2.3-4.noarch.rpm\n" +
            "Upload https://foo.host/artifactory/yum-local/theorg/theprj/another-package-4.3.2.deb\n" +
            // add some noise, we expect none will match
            "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created\n" +
            "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/other/path/some-other-package_1.3.3.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created"

        List<String> gradleRegex = [/Upload https?:\/\/.+\/(.+\.(deb|rpm))$/].toList()

        when:
        List<GenericArtifact> artifacts = ArtifactParser.getArtifactsFromLog(buildLog, gradleRegex)

        then:
        artifacts.first().fileName == "some-package-1.2.3-4.noarch.rpm"
        artifacts.last().fileName == "another-package-4.3.2.deb"
    }

    def "get multiple artifactory deb from log using default regexes"() {
        String buildLog = "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created\n" +
            "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/other/path/some-other-package_1.3.3.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64\n" +
            "[Thread 0] Artifactory response: 201 Created"
        when:
        List<GenericArtifact> artifacts = ArtifactParser.getArtifactsFromLog(buildLog, null)

        then:
        artifacts.first().fileName == "some-package_0.0.7_amd64.deb"
        artifacts.last().fileName == "some-other-package_1.3.3.7_amd64.deb"
    }

    @Unroll
    def "get single deb or rpm from log using default regexes"() {
        expect:
        List<GenericArtifact> artifacts = ArtifactParser.getArtifactsFromLog(buildLog, Collections.emptyList())

        where:
        buildLog                     || packageName
        "Successfully pushed package-name.0.0-20160531141100_amd64.deb to org/repo" ||
            "package-name.0.0-20160531141100_amd64.deb" // packagecloud
        "[Thread 0] Uploading artifact: https://foo.host/artifactory/yum-local/some/nice/path/some-package-0.0.4.1-1.x86_64.rpm"   ||
            "some-package-0.0.4.1-1.x86_64.rpm" // artifactory
        "[Thread 0] Uploading artifact: https://foo.host/artifactory/debian-local/some/nice/path/some-package_0.0.7_amd64.deb;deb.distribution=trusty;deb.component=main;deb.architecture=amd64" ||
            "some-package_0.0.7_amd64.deb" // artifactory
    }
}
