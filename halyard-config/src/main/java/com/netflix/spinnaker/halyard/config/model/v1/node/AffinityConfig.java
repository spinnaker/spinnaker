/*
 * Copyright 2019 Snap, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class AffinityConfig {
  PodAffinity podAntiAffinity;
  PodAffinity podAffinity;
  NodeAffinity nodeAffinity;

  @Data
  public static class PodAffinity {
    List<PodAffinityTerm> requiredDuringSchedulingIgnoredDuringExecution;
    List<WeightedPodAffinityTerm> preferredDuringSchedulingIgnoredDuringExecution;
  }

  @Data
  public static class NodeAffinity {
    NodeSelector requiredDuringSchedulingIgnoredDuringExecution;
    List<PreferredSchedulingTerm> preferredDuringSchedulingIgnoredDuringExecution;
  }

  @Data
  public static class NodeSelectorTerm {
    List<NodeSelectorRequirement> matchExpressions;
    List<NodeSelectorRequirement> matchFields;
  }

  @Data
  public static class NodeSelector {
    List<NodeSelectorTerm> nodeSelectorTerms;
  }

  @Data
  public static class PreferredSchedulingTerm {
    Integer weight;
    NodeSelectorTerm preference;
  }

  @Data
  public static class NodeSelectorRequirement {
    String key;
    Operator operator;
    String[] values;

    enum Operator {
      In,
      NotIn,
      Exists,
      DoesNotExist,
      Gt,
      Lt
    }
  }

  @Data
  public static class PodAffinityTerm {
    LabelSelector labelSelector;
    List<String> namespaces;
    String topologyKey;
  }

  @Data
  public static class WeightedPodAffinityTerm {
    Integer weight;
    PodAffinityTerm podAffinityTerm;
  }

  @Data
  public static class LabelSelector {
    Map<String, String> matchLabels;
    List<LabelSelectorRequirement> matchExpressions;
  }

  @Data
  public static class LabelSelectorRequirement {
    String key;
    Operator operator;
    String[] values;

    enum Operator {
      In,
      NotIn,
      Exists,
      DoesNotExist
    }
  }
}
