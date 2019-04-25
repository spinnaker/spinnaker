/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.igor.build.artifact.decorator

import com.netflix.spinnaker.igor.build.model.GenericArtifact
import spock.lang.Specification
import spock.lang.Unroll

class RpmDetailsDecoratorSpec extends Specification {

    @Unroll
    def "set version on RPMs"() {
        given:
        RpmDetailsDecorator rpmDetailsExtractor = new RpmDetailsDecorator()

        when:
        GenericArtifact genericArtifact = new GenericArtifact(file, file, file)
        rpmDetailsExtractor.decorate(genericArtifact)

        then:
        genericArtifact.version == version

        where:
        file                                        || version
        "api-4.11.4h-1.x86_64.rpm"                  || "4.11.4h-1.x86_64"
        "alsa-lib-1.0.17-1.el5.i386.rpm"            || "1.0.17-1.el5.i386"
        "openmotif22-libs-2.2.4-192.1.3.x86_64.rpm" || "2.2.4-192.1.3.x86_64"
    }

    @Unroll
    def "set name on RPMs"() {
        given:
        RpmDetailsDecorator rpmDetailsExtractor = new RpmDetailsDecorator()

        when:
        GenericArtifact genericArtifact = new GenericArtifact(file, file, file)
        rpmDetailsExtractor.decorate(genericArtifact)

        then:
        genericArtifact.name == name

        where:
        file                                        || name
        "api-4.11.4h-1.x86_64.rpm"                  || "api"
        "alsa-lib-1.0.17-1.el5.i386.rpm"            || "alsa-lib"
        "openmotif22-libs-2.2.4-192.1.3.x86_64.rpm" || "openmotif22-libs"
    }

    def "set reference on RPMs"() {
        given:
        RpmDetailsDecorator rpmDetailsExtractor = new RpmDetailsDecorator()
        String file = "api-4.11.4h-1.x86_64.rpm"

        when:
        GenericArtifact genericArtifact = new GenericArtifact(file, file, file)
        rpmDetailsExtractor.decorate(genericArtifact)

        then:
        genericArtifact.reference == file
    }

    @Unroll
    def "identifies RPM packages by reference"() {
        when:
        RpmDetailsDecorator rpmDetailsDecorator = new RpmDetailsDecorator()
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)

        then:
        rpmDetailsDecorator.handles(genericArtifact) == knownArtifact

        where:
        reference                || knownArtifact
        "test-artifact-2.13.war" || false
        "no-extension"           || false
        "rosco_0.35.0-3_all.deb" || false
        "test-2.13.deb.true.fal" || false
        null                     || false
        ""                       || false
        "api-411.4-1.x86_64.rpm" || true
    }
}
