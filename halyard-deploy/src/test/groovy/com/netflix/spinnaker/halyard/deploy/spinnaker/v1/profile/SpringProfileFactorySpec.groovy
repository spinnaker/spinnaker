/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile

import spock.lang.Specification


class SpringProfileFactorySpec extends Specification {

    def "version checks work"() {
        given:
        def subject = new SpringProfileFactory()

        expect:
        subject.spinnakerVersionSupportsPlugins(version) == should_support_plugins

        where:
        version                               | should_support_plugins
        "1.7.5"                               | false
        "1.19.3"                              | false
        "1.19.11"                             | true
        "master-20191121162350"               | false
        "master-20200503040016"               | true
        "io-codelab"                          | false
        "release-1.10.x-latest-unvalidated"   | false
        "release-1.18.x-20200314030017"       | false
        "release-1.19.x-20200403040016"       | false
        "release-1.19.x-20200503040016"       | true
        "release-1.19.x-latest-validated"     | true
        "release-1.7.x-latest-validated"      | false
        "local:1.7.5"                         | false
        "local:1.19.3"                        | false
        "local:1.19.11"                       | true
        "branch:master-20191121162350"        | false
        "branch:master-20200503040016"        | false
    }
}
