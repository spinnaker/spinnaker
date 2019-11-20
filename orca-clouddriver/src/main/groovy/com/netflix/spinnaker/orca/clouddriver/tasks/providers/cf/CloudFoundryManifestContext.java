/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@Builder(builderClassName = "CloudFoundryManifestContextBuilder", toBuilder = true)
@Value
public class CloudFoundryManifestContext implements ManifestContext {
  @Nullable private List<Map<Object, Object>> manifests;

  private Source source;

  private String manifestArtifactId;
  private Artifact manifestArtifact;
  private String manifestArtifactAccount;

  @Builder.Default private List<String> requiredArtifactIds = Collections.emptyList();
  @Builder.Default private List<BindArtifact> requiredArtifacts = Collections.emptyList();

  @Builder.Default private boolean skipExpressionEvaluation = false;

  @Override
  public List<Map<Object, Object>> getManifests() {
    return manifests;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class CloudFoundryManifestContextBuilder {}
}
