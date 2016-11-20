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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.ValidatorCollection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ValidateService {
  @Autowired
  HalconfigParser parser;

  @Autowired
  ValidatorCollection validatorCollection;

  ProblemSet validateMatchingFilter(NodeFilter filter) {
    Halconfig halconfig = parser.getConfig();
    ProblemSetBuilder psBuilder = new ProblemSetBuilder();
    recursiveValidate(psBuilder, halconfig, filter);

    return psBuilder.build();
  }

  private void recursiveValidate(ProblemSetBuilder psBuilder, Node node, NodeFilter filter) {
    log.info("Running all validators for node " + node.getNodeName() + " with class " + node.getClass());

    validatorCollection.runAllValidators(psBuilder, node);

    NodeIterator children = node.getChildren();

    Node recurse = children.getNext(filter);
    while (recurse != null) {
      recursiveValidate(psBuilder, recurse, filter);
      recurse = children.getNext(filter);
    }
  }
}
