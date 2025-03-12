/*
 * Copyright 2020 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.validators;

import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import java.util.List;
import java.util.stream.Collectors;

public class StandardCloudFoundryAttributeValidator {

  private String context;
  private ValidationErrors errors;

  public StandardCloudFoundryAttributeValidator(String context, ValidationErrors errors) {
    this.context = context;
    this.errors = errors;
  }

  public void validateRegions(String region, CloudFoundryCredentials credentials) {
    List<String> filteredRegions =
        credentials.getFilteredSpaces().stream()
            .map(s -> s.getRegion())
            .collect(Collectors.toList());
    if (!credentials.getFilteredSpaces().isEmpty()) {
      if (!filteredRegions.contains(region)) {
        errors.rejectValue(context + ".region", context + ".region." + "notValid");
      }
    }
  }
}
