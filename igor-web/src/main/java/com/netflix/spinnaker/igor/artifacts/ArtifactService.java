/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.igor.artifacts;

import com.netflix.spinnaker.igor.model.ArtifactServiceProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * An abstract service for anything that provides artifacts. This is used for manual triggers of
 * artifact driven events. You'll need to implement a list of artifact versions for a given name,
 * and retrieving a specific artifact by name and version.
 */
public interface ArtifactService {
  @Nonnull
  ArtifactServiceProvider artifactServiceProvider();

  /** Used to populate the manual trigger dropdown with options */
  @Nonnull
  List<String> getArtifactVersions(@Nonnull String name);

  /** Used to fetch a specific artifact for decorating a trigger */
  @Nonnull
  Artifact getArtifact(@Nonnull String name, @Nonnull String version);
}
