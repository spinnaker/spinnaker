/*
 * Copyright 2018 Praekelt.org
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

package com.netflix.spinnaker.halyard.config.model.v1

import com.netflix.spinnaker.halyard.core.error.v1.HalException
import com.netflix.spinnaker.halyard.core.problem.v1.Problem
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.IOUtils
import spock.lang.Specification

import com.netflix.spinnaker.halyard.core.registry.v1.ProfileRegistry
import com.netflix.spinnaker.halyard.core.registry.v1.GoogleProfileReader
import com.netflix.spinnaker.halyard.core.registry.v1.GitProfileReader
import com.netflix.spinnaker.halyard.core.registry.v1.LocalDiskProfileReader
import org.yaml.snakeyaml.Yaml
import com.fasterxml.jackson.databind.ObjectMapper

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import java.io.File


@SpringBootTest(classes = [ProfileRegistry.class, GoogleProfileReader.class, GitProfileReader.class, LocalDiskProfileReader.class, Yaml.class, ObjectMapper.class, String.class])
class LocalDiskProfileReaderSpec extends Specification {

    @Autowired
    ProfileRegistry profileRegistry

    @Autowired
    LocalDiskProfileReader localDiskProfileReader

    String localBomPath = new File(".").getCanonicalPath() + "/src/test/resources/profiles"

    void "Attempt to pick LocalDiskProfileReader from version"() {
        setup:
            String version = "local:test-version"
        when:
            def profileReader = profileRegistry.pickProfileReader("local:test-version")
        then:
            profileReader instanceof LocalDiskProfileReader
    }

    void "Attempt to parse well-formed profile path"() {
        setup:
            localDiskProfileReader.localBomPath = localBomPath
            String artifactName = "test-artifact"
            String version = "local:test-version"
            String profileName = "test-artifact-local.yml"
        when:
            localDiskProfileReader.readProfile(artifactName, version, profileName)
        then:
            true
    }

    void "Attempt to parse malformed profile path"() {
        setup:
            localDiskProfileReader.localBomPath = new File(".").getCanonicalPath() + "/src::test/resources/profiles"
            String artifactName = "test-artifact"
            String version = "local:test-version"
            String profileName = "test-artifact-local.yml"
        when:
            localDiskProfileReader.readProfile(artifactName, version, profileName)
        then:
            IOException ex = thrown()
    }

    void "Attempt to read profile from tarball"() {
        setup:
            localDiskProfileReader.localBomPath = new File(".").getCanonicalPath() + "/src/test/resources/profiles"
            String artifactName = "test-artifact"
            String version = "local:test-version"
            String profileName = "test-artifact"
        when:
            def archiveProfileStream = localDiskProfileReader.readArchiveProfile(artifactName, version, profileName)
            TarArchiveInputStream tis

            try {
                tis = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", archiveProfileStream)
            } catch (ArchiveException e) {
                throw new HalException(Problem.Severity.FATAL, "Failed to unpack tar archive", e)
            }

            def tarContents

            try {
                tis.getNextEntry()
                tarContents = IOUtils.toString(tis)
            } catch (IOException e) {
                    throw new HalException(Problem.Severity.FATAL, "Failed to read profile entry", e)
            }
            def fileInputStream =  localDiskProfileReader.readProfile(artifactName, version, profileName + ".tar.gz")
            def fileContents = IOUtils.toString(fileInputStream)
        then:
            tarContents == fileContents
    }
}