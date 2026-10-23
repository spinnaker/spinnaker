/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EcsServerGroup implements ServerGroup {

  String name;
  String type;
  String cloudProvider;
  String region;
  Boolean disabled;
  Long createdTime;
  Set<String> zones;
  Set<Instance> instances;
  Set<String> loadBalancers;
  Set<String> securityGroups;
  Map<String, Object> launchConfig;
  Image image;
  InstanceCounts instanceCounts;
  Capacity capacity;
  ImagesSummary imagesSummary;
  ImageSummary imageSummary;
  Map<String, Object> tags;
  String ecsCluster;
  TaskDefinition taskDefinition;
  String vpcId;
  AutoScalingGroup asg;
  Set<String> metricAlarms;
  Moniker moniker;

  // The running task-definition revision (the trailing number of the task-definition ARN, e.g. 42
  // for ".../my-family:42"). For the ecs-native provider this is the closest analog to the classic
  // vNNN server-group sequence, since a native service has no sequence and rolls new task-def
  // revisions in place. Null if the revision could not be parsed from the ARN.
  //
  // This and the rollout fields below are @JsonIgnore-d as top-level bean properties and instead
  // emitted through getExtraAttributes() (@JsonAnyGetter). That is deliberate: the clusters-view
  // summary payload is produced by clouddriver-web's hand-written ServerGroupViewModel DTO, which
  // copies only a fixed whitelist of fields plus whatever getExtraAttributes() returns. Routing
  // these through extraAttributes is the only way to get them onto the summary payload (and thus
  // the Deck clusters-view card header) without editing that shared, provider-agnostic DTO. On the
  // full-object/details serialization path they still appear flattened at the top level, so Deck
  // reads serverGroup.taskDefinitionRevision / rolloutState the same way in both views.
  @JsonIgnore Integer taskDefinitionRevision;

  // ECS's own rollout state for the service's PRIMARY deployment (IN_PROGRESS / COMPLETED /
  // FAILED), plumbed through from the cached Service. Lets Deck show whether ECS considers the
  // current deployment settled. Null when unavailable. (The details pane fetches this live rather
  // than from here; this cached value backs the always-on clusters-view card header.)
  @JsonIgnore String deploymentId;
  @JsonIgnore String rolloutState;
  @JsonIgnore String rolloutStateReason;

  // Whether this ECS service was deployed by the opt-in ecs-native provider. The read path uses
  // the durable ECS service tag rather than treating an unversioned name as ownership evidence.
  @JsonIgnore Boolean isNative;

  // The ACTIVE task-definition revisions of this service's family (newest first), populated only on
  // the details path for a native service (see EcsServerClusterProvider). Deck's rollback picker
  // reads these to offer an earlier revision to roll back to. Carried on the existing server-group
  // details payload so no new gate endpoint is needed. Null/empty for classic services and for the
  // list/summary (non-details) path.
  @JsonIgnore List<EcsTaskDefinitionRevision> taskDefinitionRevisions;

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  /**
   * Surfaces the ecs-native-specific fields above as flattened top-level JSON keys. Consumed both
   * by the full-object serialization path (details view) and, crucially, by clouddriver-web's
   * {@code ServerGroupViewModel} summary DTO, which forwards {@code getExtraAttributes()} via its
   * own {@code @JsonAnyGetter} -- that is how these reach the Deck clusters-view card header. Only
   * non-null values are emitted so classic {@code ecs} server groups (which never set these) add
   * nothing to their payload.
   */
  @JsonAnyGetter
  public Map<String, Object> getExtraAttributes() {
    Map<String, Object> extraAttributes = new LinkedHashMap<>();
    if (taskDefinitionRevision != null) {
      extraAttributes.put("taskDefinitionRevision", taskDefinitionRevision);
    }
    if (deploymentId != null) {
      extraAttributes.put("deploymentId", deploymentId);
    }
    if (rolloutState != null) {
      extraAttributes.put("rolloutState", rolloutState);
    }
    if (rolloutStateReason != null) {
      extraAttributes.put("rolloutStateReason", rolloutStateReason);
    }
    if (Boolean.TRUE.equals(isNative)) {
      extraAttributes.put("isNative", true);
    }
    if (taskDefinitionRevisions != null) {
      extraAttributes.put("taskDefinitionRevisions", taskDefinitionRevisions);
    }
    return extraAttributes;
  }

  @Data
  @NoArgsConstructor
  public static class AutoScalingGroup {
    Integer minSize;
    Integer maxSize;
    Integer desiredCapacity;
  }

  @Data
  @NoArgsConstructor
  public static class Image {
    public String imageId;
    public String name;
  }
}
