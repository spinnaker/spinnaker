/*
 * Copyright 2018 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.validation.Errors;

abstract class CommonValidator extends DescriptionValidator {
  String errorKey;

  public CommonValidator(String erroryKey) {
    this.errorKey = erroryKey;
  }

  void validateRegions(
      AbstractAmazonCredentialsDescription credentialsDescription,
      Collection<String> regionNames,
      Errors errors,
      String attributeName) {
    if (regionNames.isEmpty()) {
      rejectValue(errors, attributeName, "empty");
    } else {
      Set<String> validRegions =
          credentialsDescription.getCredentials().getRegions().stream()
              .map(AmazonCredentials.AWSRegion::getName)
              .collect(Collectors.toSet());

      if (!validRegions.isEmpty() && !validRegions.containsAll(regionNames)) {
        rejectValue(errors, attributeName, "not.configured");
      }
    }
  }

  boolean validateCredentials(
      AbstractAmazonCredentialsDescription credentialsDescription,
      Errors errors,
      String attributeName) {
    if (credentialsDescription.getCredentials() == null) {
      rejectValue(errors, attributeName, "not.nullable");
      return false;
    }
    return true;
  }

  void validateCapacity(Errors errors, ServerGroup.Capacity capacity) {
    if (capacity != null) {
      boolean desiredNotNull = capacity.getDesired() != null;
      boolean minNotNull = capacity.getMin() != null;
      boolean maxNotNull = capacity.getMax() != null;

      if (!desiredNotNull) {
        rejectValue(errors, "capacity.desired", "not.nullable");
      }
      if (!minNotNull) {
        rejectValue(errors, "capacity.min", "not.nullable");
      }
      if (!maxNotNull) {
        rejectValue(errors, "capacity.max", "not.nullable");
      }

      positivityCheck(desiredNotNull, capacity.getDesired(), "desired", errors);
      positivityCheck(minNotNull, capacity.getMin(), "min", errors);
      positivityCheck(maxNotNull, capacity.getMax(), "max", errors);

      if (minNotNull && maxNotNull) {
        if (capacity.getMin() > capacity.getMax()) {
          rejectValue(errors, "capacity.min.max.range", "invalid");
        }

        if (desiredNotNull && capacity.getDesired() > capacity.getMax()) {
          rejectValue(errors, "capacity.desired", "exceeds.max");
        }

        if (desiredNotNull && capacity.getDesired() < capacity.getMin()) {
          rejectValue(errors, "capacity.desired", "less.than.min");
        }
      }

    } else {
      rejectValue(errors, "capacity", "not.nullable");
    }
  }

  void rejectValue(Errors errors, String field, String reason) {
    errors.rejectValue(field, errorKey + "." + field + "." + reason);
  }

  private void positivityCheck(
      boolean isNotNull, Integer capacity, String fieldName, Errors errors) {
    if (isNotNull && capacity < 0) {
      rejectValue(errors, "capacity." + fieldName, "invalid");
    }
  }
}
