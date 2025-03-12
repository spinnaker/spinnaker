/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Builder(builderClassName = "RunJobManifestContextBuilder", toBuilder = true)
@JsonDeserialize(builder = RunJobManifestContext.RunJobManifestContextBuilder.class)
@Value
public class RunJobManifestContext implements ManifestContext {
  @Nullable private Map<Object, Object> manifest;
  @Nullable private List<Map<Object, Object>> manifests;

  @Builder.Default private Source source = Source.Text;

  private String manifestArtifactId;
  private Artifact manifestArtifact;
  private String manifestArtifactAccount;

  private List<String> requiredArtifactIds;
  private List<BindArtifact> requiredArtifacts;

  @Builder.Default private boolean skipExpressionEvaluation = false;

  @Override
  public List<Map<Object, Object>> getManifests() {
    if (manifest != null) {
      return Arrays.asList(manifest);
    }
    return manifests;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class RunJobManifestContextBuilder {}
}
