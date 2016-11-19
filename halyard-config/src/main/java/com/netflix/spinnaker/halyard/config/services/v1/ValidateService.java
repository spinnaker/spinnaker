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
import com.netflix.spinnaker.halyard.config.model.v1.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.StaticValidator;
import com.netflix.spinnaker.halyard.config.model.v1.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeCoordinates;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValidateService {
  @Autowired
  HalconfigParser parser;

  public void validateAll(NodeFilter filter) {
    StaticValidator v = new StaticValidator();
    Halconfig halconfig = parser.getConfig();
    NodeCoordinates coordinates = new NodeCoordinates();
    recursiveValidate(filter, halconfig, v, coordinates);
  }

  private void recursiveValidate(NodeFilter filter, Node node, Validator v, NodeCoordinates coordinates) {
    coordinates = coordinates.refine(node);
    v.getProblemSetBuilder().setCoordinates(coordinates);
    node.accept(v);

    NodeIterator iterator = node.getIterator();

    Node recurse = iterator.getNext(filter);
    while (recurse != null) {
      recursiveValidate(filter, recurse, v, coordinates);
    }
  }
}
