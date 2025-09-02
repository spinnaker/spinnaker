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
package com.netflix.spinnaker.kork.artifacts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ArtifactTypes helps describe what an artifact is, the format, and whether it is stored somewhere
 * or not.
 *
 * <p>An artifact type is formated as 'location-type/artifact-type/encoding' with artifact-type
 * being optional.
 */
@RequiredArgsConstructor
public enum ArtifactTypes {
  /**
   * Embedded artifact types.
   *
   * <p>These enums represent artifacts that are stored within the execution context.
   */
  EMBEDDED_BASE64(CommonAffixes.EMBEDDED.asString() + "/" + CommonAffixes.BASE64.asString()),
  EMBEDDED_MAP_BASE64(
      CommonAffixes.EMBEDDED.asString() + "/map/" + CommonAffixes.BASE64.asString()),

  /**
   * Remote stored artifact types.
   *
   * <p>These enums are used when the artifact store stores the artifact. The stored artifact is of
   * some embedded type, which will be replaced with the appropriate remote type. For consistency,
   * all embedded artifacts should match to their appropriate remote artifact type. This means
   * "embedded/foo/artifact" will map to a stored artifact of "remote/foo/artifact".
   */
  REMOTE_BASE64(CommonAffixes.REMOTE.asString() + "/" + CommonAffixes.BASE64.asString()),
  REMOTE_MAP_BASE64(CommonAffixes.REMOTE.asString() + "/map/" + CommonAffixes.BASE64.asString()),
  ;

  /**
   * CommonAffixes describe the overall structure of the artifact.
   *
   * <p>The location type describes where it is located, e.g., remote or embedded. * An embedded
   * location type suggests that the artifact is stored within the execution context. * A remote
   * location type suggests that the artifact is stored is some database.
   *
   * <p>The artifact type gives some context as to what the artifact is. For example, if an artifact
   * is a manifest and embedded in the execution context, then a proper artifact type would be
   * described like embedded/manifest/base64. Artifact type is an optional parameter and can be
   * excluded.
   *
   * <p>The encoding describes what the encoding format is. If the artifact is base64 encoded in the
   * execution context, then the artifact type must include that information.
   */
  @RequiredArgsConstructor
  public enum CommonAffixes {
    // prefixes
    EMBEDDED("embedded"),
    REMOTE("remote"),
    // suffixes
    BASE64("base64"),
    ;

    private final String value;

    public String asString() {
      return this.value;
    }
  }

  @Getter private final String mimeType;

  /**
   * Determines if the given type string represents an embedded artifact.
   *
   * @param t The type string to check
   * @return true if the type string starts with the embedded prefix, false otherwise
   */
  public static boolean isEmbedded(String t) {
    return t != null && t.startsWith(CommonAffixes.EMBEDDED.asString() + "/");
  }

  /**
   * Determines if the given type string represents a remote artifact.
   *
   * @param t The type string to check
   * @return true if the type string starts with the remote prefix, false otherwise
   */
  public static boolean isRemote(String t) {
    return t != null && t.startsWith(CommonAffixes.REMOTE.asString() + "/");
  }
}
