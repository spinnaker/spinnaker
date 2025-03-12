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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class NodeDiff {
  String location;
  ChangeType changeType;
  List<FieldDiff> fieldDiffs = new ArrayList<>();
  List<NodeDiff> nodeDiffs = new ArrayList<>();

  public NodeDiff setNode(Node node) {
    location = node.getNameToClass(DeploymentConfiguration.class);
    return this;
  }

  public NodeDiff addFieldDiff(FieldDiff f) {
    fieldDiffs.add(f);
    return this;
  }

  public NodeDiff addNodeDiff(NodeDiff n) {
    nodeDiffs.add(n);
    return this;
  }

  public enum ChangeType {
    ADDED,
    REMOVED,
    EDITED
  }

  @Data
  public static class FieldDiff {
    String fieldName;
    Object oldValue;
    Object newValue;
  }
}
