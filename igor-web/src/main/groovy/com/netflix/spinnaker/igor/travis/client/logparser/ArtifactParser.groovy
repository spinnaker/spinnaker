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
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter
import java.util.regex.Matcher

@CompileStatic
@Slf4j
class ArtifactParser {

    static List<String> DEFAULT_REGEXES = [
        /Uploading artifact: https?:\/\/.+\/(.+\.(deb|rpm)).*$/,
        /Successfully pushed (.+\\.(deb|rpm)) to .*/].toList()

    /**
     * Parse the build log using the given regular expressions.  If they are
     * null, or empty, then DEFAULT_REGEXES will be used, matching on artifacts
     * uploading from the `art` CLI tool.
     */
    static List<GenericArtifact> getArtifactsFromLog(String buildLog, Iterable<String> regexes) {
        final List<GenericArtifact> artifacts = new ArrayList<GenericArtifact>()
        if (regexes == null || regexes.size() <= 0) {
            regexes = DEFAULT_REGEXES
        }
        buildLog.split('\n').each { line ->
            Matcher m
            for (def regex : regexes) {
                if ((m = line =~ regex)) {
                    def match = m.group(1)
                    log.debug "Found artifact: ${match}"
                    artifacts.add new GenericArtifact(match, match, match)
                }
            }
        }
        artifacts
    }
}
