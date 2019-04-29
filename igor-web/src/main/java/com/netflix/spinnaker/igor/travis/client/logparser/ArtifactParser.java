/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.logparser;

import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactParser {

  private static final Logger log = LoggerFactory.getLogger(ArtifactParser.class);

  private static final List<String> DEFAULT_REGEXES =
      Collections.unmodifiableList(
          Arrays.asList(
              "Uploading artifact: https?:\\/\\/.+\\/(.+\\.(deb|rpm)).*$",
              "Successfully pushed (.+\\.(deb|rpm)) to .*"));

  /**
   * Parse the build log using the given regular expressions. If they are null, or empty, then
   * DEFAULT_REGEXES will be used, matching on artifacts uploading from the `art` CLI tool.
   */
  public static List<GenericArtifact> getArtifactsFromLog(
      String buildLog, Collection<String> regexes) {
    final List<Pattern> finalRegexes =
        (regexes == null || regexes.isEmpty() ? DEFAULT_REGEXES : regexes)
            .stream().map(Pattern::compile).collect(Collectors.toList());
    return Arrays.stream(buildLog.split("\n"))
        .flatMap(
            line ->
                finalRegexes.stream()
                    .map(regex -> regex.matcher(line))
                    .filter(Matcher::find)
                    .map(match -> match.group(1))
                    .peek(match -> log.debug("Found artifact: " + match))
                    .distinct()
                    .map(match -> new GenericArtifact(match, match, match))
                    .collect(Collectors.toList())
                    .stream())
        .collect(Collectors.toList());
  }
}
