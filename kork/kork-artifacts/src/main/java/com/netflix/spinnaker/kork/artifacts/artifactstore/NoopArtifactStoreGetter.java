/*
 * Copyright 2023 Salesforce Inc.
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

/** A no-op ArtifactStoreGetter. In other words, don't actually get the artifact. */
public class NoopArtifactStoreGetter implements ArtifactStoreGetter {

  public Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorators) {
    throw new IllegalStateException(
        "unable to retrieve artifact "
            + uri.toString()
            + " since there's no artifact store getter configured");
  }
}
