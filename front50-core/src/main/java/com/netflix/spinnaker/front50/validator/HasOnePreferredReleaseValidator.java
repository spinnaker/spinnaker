/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class HasOnePreferredReleaseValidator implements PluginInfoValidator {

  @Override
  public void validate(PluginInfo pluginInfo, Errors validationErrors) {
    long preferredReleases =
        pluginInfo.getReleases().stream().filter(PluginInfo.Release::isPreferred).count();

    if (preferredReleases > 1) {
      validationErrors.rejectValue(
          "preferred",
          "pluginInfo.releases.invalid",
          "Plugin Info Releases can have only one preferred release.");
    }
  }
}
