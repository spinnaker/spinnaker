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

import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser;
import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser.IllegalVersionRequirementsOperator;
import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser.InvalidPluginVersionRequirementException;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class HasValidRequiresFieldsValidator implements PluginInfoValidator {

  @Override
  public void validate(PluginInfo pluginInfo, Errors validationErrors) {

    pluginInfo
        .getReleases()
        .forEach(
            release -> {
              try {
                VersionRequirementsParser.INSTANCE.parseAll(release.getRequires());
              } catch (InvalidPluginVersionRequirementException invalidPluginVersionRequirement) {
                validationErrors.reject(
                    "pluginInfo.id.invalidPluginVersionRequirement",
                    invalidPluginVersionRequirement.getMessage());
              } catch (IllegalVersionRequirementsOperator illegalVersionRequirementOperator) {
                validationErrors.reject(
                    "pluginInfo.id.illegalVersionRequirementOperator",
                    illegalVersionRequirementOperator.getMessage());
              }
            });
  }
}
