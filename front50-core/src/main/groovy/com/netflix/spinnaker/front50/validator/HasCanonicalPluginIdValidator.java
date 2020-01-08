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
package com.netflix.spinnaker.front50.validator;

import com.netflix.spinnaker.front50.model.pluginartifact.PluginArtifact;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class HasCanonicalPluginIdValidator implements PluginArtifactValidator {

  private final Pattern pattern = Pattern.compile("[\\w]+\\.[\\w]+");

  @Override
  public void validate(PluginArtifact pluginArtifact, Errors validationErrors) {
    if (!pattern.matcher(pluginArtifact.getId()).matches()) {
      validationErrors.rejectValue(
          "id",
          "pluginArtifact.id.invalid",
          "Plugin Artifact must have a '{namespace}.{id}' canonical format");
    }
  }
}
