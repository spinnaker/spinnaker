/*
 * Copyright 2016 Google, Inc.
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

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This collects all validators that have been defined so far, and tries to apply all matching ones
 * to the input node.
 */
@Slf4j
@Component
public class ValidatorCollection {
  @Autowired(required = false)
  private List<Validator> validators = new ArrayList<>();

  /**
   * Runs every validator defined against the given node.
   *
   * @param psBuilder contains the problems encountered during validation so far.
   * @param node is the node being validated.
   * @return # of validators run (for logging purposes).
   */
  public int runAllValidators(ConfigProblemSetBuilder psBuilder, Node node) {
    psBuilder.setNode(node);
    int validatorRuns = 0;
    for (Validator validator : validators) {
      validatorRuns += runMatchingValidators(psBuilder, validator, node, node.getClass()) ? 1 : 0;
    }

    return validatorRuns;
  }

  /**
   * Walk up the object hierarchy, running this validator whenever possible. The idea is, perhaps we
   * were passed a Kubernetes account, and want to run both the standard Kubernetes account
   * validator to see if the kubeconfig is valid, as well as the super-classes Account validator to
   * see if the account name is valid.
   *
   * @param psBuilder contains the list of problems encountered.
   * @param validator is the validator to be run.
   * @param node is the subject of validation.
   * @param c is some super(inclusive) class of node.
   * @return true iff the validator ran on the node (for logging purposes).
   */
  private boolean runMatchingValidators(
      ConfigProblemSetBuilder psBuilder, Validator validator, Node node, Class c) {
    if (c == Object.class) {
      return false;
    }

    try {
      Method m = validator.getClass().getMethod("validate", ConfigProblemSetBuilder.class, c);
      DaemonTaskHandler.message(
          "Validating " + node.getNodeName() + " with " + validator.getClass().getSimpleName());
      m.invoke(validator, psBuilder, node);
      return true;
    } catch (InvocationTargetException | NoSuchMethodException e) {
      // Do nothing, odds are most validators don't validate every class.
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Failed to invoke validate() on \""
              + validator.getClass().getSimpleName()
              + "\" for node \""
              + c.getSimpleName(),
          e);
    }

    return runMatchingValidators(psBuilder, validator, node, c.getSuperclass());
  }
}
