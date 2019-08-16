/*
 *
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.manageddelivery.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ResourceEvent.ResourceCreated.class, name = "ResourceCreated"),
  @JsonSubTypes.Type(value = ResourceEvent.ResourceUpdated.class, name = "ResourceUpdated"),
  @JsonSubTypes.Type(value = ResourceEvent.ResourceMissing.class, name = "ResourceMissing"),
  @JsonSubTypes.Type(value = ResourceEvent.ResourceDeleted.class, name = "ResourceDeleted"),
  @JsonSubTypes.Type(
      value = ResourceEvent.ResourceActuationLaunched.class,
      name = "ResourceActuationLaunched"),
  @JsonSubTypes.Type(
      value = ResourceEvent.ResourceDeltaDetected.class,
      name = "ResourceDeltaDetected"),
  @JsonSubTypes.Type(
      value = ResourceEvent.ResourceDeltaResolved.class,
      name = "ResourceDeltaResolved")
})
@Data
public class ResourceEvent {
  String uid;
  String apiVersion;
  String kind;
  String name;
  String timestamp;

  /** A new resource was registered for management. */
  @Data
  @JsonTypeName("ResourceCreated")
  public static class ResourceCreated extends ResourceEvent {}

  /** A managed resource does not currently exist in the cloud. */
  @Data
  @JsonTypeName("ResourceMissing")
  public static class ResourceMissing extends ResourceEvent {
    ResourceState state;
  }

  /** A managed resource was deleted. */
  @Data
  @JsonTypeName("ResourceDeleted")
  public static class ResourceDeleted extends ResourceEvent {}

  /**
   * The desired state of a resource was updated.
   *
   * <p>[delta] The difference between the "base" spec (previous version) and "working" spec (the
   * updated version).
   */
  @Data
  @JsonTypeName("ResourceUpdated")
  public static class ResourceUpdated extends ResourceEvent {
    Map<String, Object> delta;
  }

  /**
   * The desired and actual states of a managed resource now match where previously there was a
   * delta (or the resource did not exist).
   */
  @Data
  @JsonTypeName("ResourceDeltaResolved")
  public static class ResourceDeltaResolved extends ResourceEvent {
    ResourceState state;
  }

  /**
   * A difference between the desired and actual state of a managed resource was detected.
   *
   * <p>[delta] The difference between the "base" spec (desired) and "working" spec (actual).
   */
  @Data
  @JsonTypeName("ResourceDeltaDetected")
  public static class ResourceDeltaDetected extends ResourceEvent {
    Map<String, Object> delta;
    ResourceState state;
  }

  /**
   * A task or tasks were launched to resolve a mismatch between desired and actual state of a
   * managed resource.
   */
  @Data
  @JsonTypeName("ResourceActuationLaunched")
  public static class ResourceActuationLaunched extends ResourceEvent {
    String plugin;
    List<Task> tasks;
  }

  @Data
  public static class Task {
    String id;
    String name;
  }
}

enum ResourceState {
  Ok,
  Diff,
  Missing,
  Error
}
