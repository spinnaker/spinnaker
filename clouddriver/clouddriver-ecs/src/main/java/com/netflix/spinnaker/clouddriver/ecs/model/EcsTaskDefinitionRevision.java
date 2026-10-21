/*
 * Copyright 2026 Spinnaker contributors.
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

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One task-definition revision in a family, as listed live from {@code ListTaskDefinitions} for the
 * {@code ecs-native} rollback picker. Unlike the classic ECS provider (which rolls back by
 * re-enabling a disabled versioned service), an {@code ecs-native} service is a single durable
 * service, so "rolling back" means pointing that service at an earlier task-definition revision of
 * the same family. This is the list of candidates the picker offers.
 *
 * <p>Purely a read/view model for the picker; the actual rollback is an in-place {@code
 * UpdateService} keyed by {@link #getTaskDefinitionArn()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcsTaskDefinitionRevision {
  /** Full task-definition ARN, e.g. {@code arn:aws:ecs:...:task-definition/my-family:42}. */
  String taskDefinitionArn;

  /** Task-definition family (the part shared across revisions), e.g. {@code my-family}. */
  String family;

  /** Revision number within the family (the trailing integer of the ARN). */
  Integer revision;

  /**
   * Container images this revision runs, in container-definition order. Shown in the picker so an
   * operator can tell revisions apart by what they deploy.
   */
  @Builder.Default List<String> containerImages = new ArrayList<>();

  /**
   * Whether this is the revision the service is currently running. The picker uses this to mark the
   * current revision and to keep an operator from "rolling back" to what is already deployed.
   */
  boolean current;
}
