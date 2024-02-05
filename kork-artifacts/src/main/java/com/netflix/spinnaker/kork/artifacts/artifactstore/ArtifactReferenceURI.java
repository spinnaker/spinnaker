/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

/**
 * A URI that can parse and allow for ArtifactStorage to easily get specific information about the
 * URI
 */
@Builder
@Getter
public class ArtifactReferenceURI {
  /**
   * uriScheme is used as an HTTP scheme to let us further distinguish a String that is a URI to an
   * artifact. This is helpful in determining what is an artifact since sometimes we are only given
   * a string rather than a full artifact.
   */
  private static final String uriScheme = "ref://";

  private final List<String> uriPaths;

  public String uri() {
    return uriScheme + paths();
  }

  public String paths() {
    return Strings.join(uriPaths, '/');
  }

  /** Used to determine whether a String is in the artifact reference URI format. */
  public static boolean is(String reference) {
    return reference.startsWith(uriScheme);
  }

  public static ArtifactReferenceURI parse(String reference) {
    String noSchemeURI = StringUtils.removeStart(reference, uriScheme);
    String[] paths = StringUtils.split(noSchemeURI, '/');
    return ArtifactReferenceURI.builder().uriPaths(Arrays.asList(paths)).build();
  }
}
