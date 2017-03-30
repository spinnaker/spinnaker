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

package com.netflix.spinnaker.igor.build.artifact.decorator

import com.netflix.spinnaker.igor.build.model.GenericArtifact
import spock.lang.Specification
import spock.lang.Unroll

class DebDetailsDecoratorSpec extends Specification {

    @Unroll
    def "identifies version from DEBs"() {
        given:
        DebDetailsDecorator debDetailsDecorator = new DebDetailsDecorator()

        when:
        GenericArtifact genericArtifact = new GenericArtifact(file, file, file)
        debDetailsDecorator.decorate(genericArtifact)

        then:
        genericArtifact.version == version

        where:
        file                           || version
        "api_1.1.1-h01.sha123_all.deb" || "1.1.1-h01.sha123"
    }

    @Unroll
    def "identifies name from DEBs"() {
        given:
        DebDetailsDecorator debDetailsDecorator = new DebDetailsDecorator()

        when:
        GenericArtifact genericArtifact = new GenericArtifact(file, file, file)
        debDetailsDecorator.decorate(genericArtifact)

        then:
        genericArtifact.name == name

        where:
        file                           || name
        "api_1.1.1-h01.sha123_all.deb" || "api"
    }

    def "identifies reference from DEBs"() {
        given:
        DebDetailsDecorator debDetailsDecorator = new DebDetailsDecorator()
        String file = "api_1.1.1-h01.sha123_all.deb"

        when:
        GenericArtifact genericArtifact = new GenericArtifact(file, file, file)
        debDetailsDecorator.decorate(genericArtifact)

        then:
        genericArtifact.reference == file
    }

    @Unroll
    def "identifies DEB packages by reference"() {
        when:
        DebDetailsDecorator debDetailsDecorator = new DebDetailsDecorator()
        GenericArtifact genericArtifact = new GenericArtifact(reference, reference, reference)

        then:
        debDetailsDecorator.handles(genericArtifact) == knownArtifact

        where:
        reference                || knownArtifact
        "test-artifact-2.13.war" || false
        "no-extension"           || false
        "rosco_0.35.0-3_all.deb" || true
        "test-2.13.deb.true.fal" || false
        null                     || false
        ""                       || false
    }
}
