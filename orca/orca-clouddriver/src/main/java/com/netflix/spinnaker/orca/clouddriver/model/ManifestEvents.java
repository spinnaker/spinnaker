/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode
@Builder
@AllArgsConstructor
@NoArgsConstructor
public final class ManifestEvents {
  private String kind;
  private String apiVersion;
  private String reason;
  private String message;

  private String type;

  private int count;

  private String firstTimestamp;
  private String lastTimestamp;

  private InvolvedObject involvedObject;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
final class InvolvedObject {
  private String kind;
  private String namespace;
  private String name;
  private String fieldPath;
  private String apiVersion;
}
