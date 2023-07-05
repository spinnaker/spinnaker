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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;

public abstract class ArtifactStoreURIBuilder {
  /**
   * uriScheme is used as an HTTP scheme to let us further distinguish a String that is a URI to an
   * artifact. This is helpful in determining what is an artifact since sometimes we are only given
   * a string rather than a full artifact.
   */
  public static final String uriScheme = "ref";

  /**
   * Returns the remote artifact URI that will be associated with some artifact.
   *
   * @param context is the context in which this artifact was run in, e.g. the application.
   * @param artifact that will be associated with the generated URI.
   * @return the remote URI
   */
  public abstract String buildArtifactURI(String context, Artifact artifact);

  /**
   * buildRawURI is used when you have the raw path and context. This method just simply returns the
   * properly formatted URI using the URI builder that extends this class.
   *
   * <p>This function is primarily used in clouddriver when deck is asking for the raw artifact to
   * be displayed. Since we don't have the artifact, but only the context and some raw ID from the
   * gate endpoint,
   *
   * <pre>/context/hash</pre>
   *
   * <p>we need to reconstruct the full remote URI in clouddriver.
   *
   * <pre>{@code
   * String application = "my-spinnaker-application";
   * String artifactSHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
   *
   * ArtifactStoreURIBuilder uriBuilder = new ArtifactStoreURISHA256Builder();
   * // returns ref://my-spinnaker-application/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
   * String uriString = uriBuilder.buildRawURI(application, artifactSHA256);
   * }</pre>
   *
   * @param context is the context in which this artifact was run in, e.g. the application.
   * @param raw is the identifier used in the URL, e.g. the hash.
   * @return a properly formatted artifact store URI
   */
  public abstract String buildRawURI(String context, String raw);
}
