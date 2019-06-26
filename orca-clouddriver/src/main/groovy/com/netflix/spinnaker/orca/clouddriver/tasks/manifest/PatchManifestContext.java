/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Value
@JsonDeserialize(builder = PatchManifestContext.PatchManifestContextBuilder.class)
@Builder(builderClassName = "PatchManifestContextBuilder", toBuilder = true)
public class PatchManifestContext implements ManifestContext {
  private Map<Object, Object> patchBody;
  private Source source;

  private String manifestArtifactId;
  private Artifact manifestArtifact;
  private String manifestArtifactAccount;
  private String manifestName;

  private List<String> requiredArtifactIds;
  private List<BindArtifact> requiredArtifacts;

  @Builder.Default private boolean skipExpressionEvaluation = false;

  @Nullable
  @Override
  public List<Map<Object, Object>> getManifests() {
    return Collections.singletonList(patchBody);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class PatchManifestContextBuilder {}
}
