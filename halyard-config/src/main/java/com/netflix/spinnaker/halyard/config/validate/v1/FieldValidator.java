/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Slf4j
public class FieldValidator extends Validator<Node> {
  @Override
  public void validate(ConfigProblemSetBuilder p, Node n) {
    validateFieldForSpinnakerVersion(p, n);
  }
  
  private void validateFieldForSpinnakerVersion(ConfigProblemSetBuilder p, Node n) {
    DeploymentConfiguration deploymentConfiguration = n.parentOfType(DeploymentConfiguration.class);
    String spinnakerVersion = deploymentConfiguration.getVersion();
    if (spinnakerVersion == null) {
      return;
    }
    
    Class clazz = n.getClass();
    while (clazz != Object.class) {
      Class finalClazz = clazz;
      Arrays.stream(clazz.getDeclaredFields())
          .forEach(field -> {
            ValidForSpinnakerVersion annotation = field.getDeclaredAnnotation(ValidForSpinnakerVersion.class);
            try {
              field.setAccessible(true);
              Object v = field.get(n);
              boolean fieldNotValid = v != null &&
                  annotation != null &&
                  Versions.lessThan(spinnakerVersion, annotation.lowerBound());

              // If the field was set to false, it's assumed it's not enabling a restricted feature
              if (fieldNotValid && (v instanceof Boolean) && !((Boolean) v)) {
                fieldNotValid = false;
              }

              if (fieldNotValid) {
                p.addProblem(
                    Problem.Severity.WARNING,
                    "Field " + finalClazz.getSimpleName() + "." + field.getName() + " not supported for Spinnaker version " + spinnakerVersion + ": " + annotation.message()
                ).setRemediation("Use at least " + annotation.lowerBound() + " (It may not have been released yet).");
              }
            } catch (IllegalArgumentException /* Probably using nightly build */ | IllegalAccessException /* Probably shouldn't happen */ e) {
              log.warn("Error validating field " + finalClazz.getSimpleName() + "." + field.getName() + ": ", e);
            }
          });
      clazz = clazz.getSuperclass();
    }
  }
}
