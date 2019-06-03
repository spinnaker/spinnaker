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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.ValidForSpinnakerVersion;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import java.util.Arrays;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

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
          .forEach(
              field -> {
                ValidForSpinnakerVersion annotation =
                    field.getDeclaredAnnotation(ValidForSpinnakerVersion.class);
                try {
                  field.setAccessible(true);
                  Object v = field.get(n);
                  boolean fieldNotValid = false;
                  String invalidFieldMessage = "";
                  String remediation = "";
                  if (v != null && annotation != null) {
                    if (Versions.lessThan(spinnakerVersion, annotation.lowerBound())) {
                      fieldNotValid = true;
                      invalidFieldMessage = annotation.tooLowMessage();
                      remediation =
                          "Use at least "
                              + annotation.lowerBound()
                              + " (It may not have been released yet).";
                    } else if (!StringUtils.equals(annotation.upperBound(), "")
                        && Versions.greaterThanEqual(spinnakerVersion, annotation.upperBound())) {
                      fieldNotValid = true;
                      invalidFieldMessage = annotation.tooHighMessage();
                      remediation = "You no longer need this.";
                    }
                  }

                  // If the field was set to false, it's assumed it's not enabling a restricted
                  // feature
                  if (fieldNotValid && (v instanceof Boolean) && !((Boolean) v)) {
                    fieldNotValid = false;
                  }

                  // If the field is a collection, it may be empty
                  if (fieldNotValid && (v instanceof Collection) && ((Collection) v).isEmpty()) {
                    fieldNotValid = false;
                  }

                  if (fieldNotValid) {
                    p.addProblem(
                            Problem.Severity.WARNING,
                            "Field "
                                + finalClazz.getSimpleName()
                                + "."
                                + field.getName()
                                + " not supported for Spinnaker version "
                                + spinnakerVersion
                                + ": "
                                + invalidFieldMessage)
                        .setRemediation(remediation);
                  }
                } catch (NumberFormatException /* Probably using nightly build */ e) {
                  log.info("Nightly builds do not contain version information.");
                } catch (IllegalAccessException /* Probably shouldn't happen */ e) {
                  log.warn(
                      "Error validating field "
                          + finalClazz.getSimpleName()
                          + "."
                          + field.getName()
                          + ": ",
                      e);
                }
              });
      clazz = clazz.getSuperclass();
    }
  }
}
