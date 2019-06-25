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

  @Data
  @JsonTypeName("ResourceCreated")
  public static class ResourceCreated extends ResourceEvent {}

  @Data
  @JsonTypeName("ResourceMissing")
  public static class ResourceMissing extends ResourceEvent {}

  @Data
  @JsonTypeName("ResourceDeltaResolved")
  public static class ResourceDeltaResolved extends ResourceEvent {}

  @Data
  @JsonTypeName("ResourceDeltaDetected")
  public static class ResourceDeltaDetected extends ResourceEvent {
    Map<String, Object> delta;
  }

  @Data
  @JsonTypeName("ResourceActuationLaunched")
  public static class ResourceActuationLaunched extends ResourceEvent {
    String plugin;
    List<String> tasks;
  }

  @Data
  @JsonTypeName("ResourceUpdated")
  public static class ResourceUpdated extends ResourceEvent {
    Map<String, Object> delta;
  }
}
