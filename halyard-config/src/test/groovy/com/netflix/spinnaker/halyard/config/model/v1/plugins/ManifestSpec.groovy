/*
 * Copyright 2019 Armory, Inc
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

package com.netflix.spinnaker.halyard.config.model.v1.plugins

import com.netflix.spinnaker.halyard.core.error.v1.HalException
import spock.lang.Specification

class ManifestSpec extends Specification {

    void "plugin manifests must have the required fields"() {
        setup:
        Manifest manifest = new Manifest();

        when:
        manifest.setJars(jars)
        manifest.setManifestVersion(manifestVersion)
        manifest.setName(name)
        manifest.setOptions(options)
        manifest.validate()

        then:
        thrown HalException

        where:
        name      | manifestVersion | jars                 | options
        "foo"     | "plugins/v1"    | Arrays.asList("jar") | null
        "foo/bar" | null            | Arrays.asList("jar") | null
        "foo/bar" | "plugins/v1"    | null                 | null
    }

    void "plugin manifests pass validation"() {
        setup:
        Manifest manifest = new Manifest();

        when:
        manifest.setJars(Arrays.asList("one", "two"))
        manifest.setManifestVersion("plugins/v1")
        manifest.setName("foo/bar")
        manifest.validate()

        then:
        noExceptionThrown()

    }
}
