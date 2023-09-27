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
  private final String scheme;
  private final List<String> uriPaths;

  public String uri() {
    return String.format("%s://%s", scheme, paths());
  }

  public String paths() {
    return Strings.join(uriPaths, '/');
  }

  public static ArtifactReferenceURI parse(String reference) {
    String noSchemeURI =
        StringUtils.removeStart(reference, ArtifactStoreURIBuilder.uriScheme + "://");
    String[] paths = StringUtils.split(noSchemeURI, '/');
    return ArtifactReferenceURI.builder()
        .scheme(ArtifactStoreURIBuilder.uriScheme)
        .uriPaths(Arrays.asList(paths))
        .build();
  }
}
