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

import static com.netflix.spinnaker.front50.model.plugininfo.PluginInfo.Release.SUPPORTS_PATTERN;
import static java.lang.String.format;

import com.netflix.spinnaker.front50.model.plugininfo.PluginInfo;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class HasValidRequiresFieldsValidator implements PluginInfoValidator {

  private static final List<String> VALID_OPERATORS = Arrays.asList("<", ">", ">=", "<=");

  @Override
  public void validate(PluginInfo pluginInfo, Errors validationErrors) {

    pluginInfo
        .getReleases()
        .forEach(
            release -> {
              Arrays.stream(release.getRequires().split(","))
                  .forEach(
                      requires -> {
                        Matcher m = SUPPORTS_PATTERN.matcher(requires.trim());
                        if (!m.matches()) {
                          validationErrors.reject(
                              "pluginInfo.releases.invalidRequiresFormat",
                              format(
                                  "Invalid Release requires field formatting (requires '%s')",
                                  SUPPORTS_PATTERN.pattern()));
                          return;
                        }

                        if (!VALID_OPERATORS.contains(
                            m.group(PluginInfo.Release.SUPPORTS_PATTERN_OPERATOR_GROUP))) {
                          validationErrors.reject(
                              "pluginInfo.releases.invalidRequiresOperator",
                              format(
                                  "Invalid Release requires comparison operator (requires one of: %s)",
                                  VALID_OPERATORS));
                        }
                      });
            });
  }
}
