/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Used to hold artifact handlers by type used by the entity storage system */
@Builder
@Getter
public class ArtifactHandlerLists {
  @Builder.Default
  /** handlers that are specific to maps and will be used when modifying map (de)serializers. */
  private List<ArtifactHandler> mapHandlers = List.of();

  @Builder.Default
  /** handlers that are specific to lists and will be used when modifying list (de)serializers. */
  private List<ArtifactHandler> collectionHandlers = List.of();
}
