/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@JsonDeserialize(builder = Task.TaskBuilder.class)
@Builder
public class Task {
  private String guid;
  private String name;
  private State state;
  private ZonedDateTime createdAt;
  private ZonedDateTime updatedAt;
  private Map<String, Link> links;

  public enum State {
    SUCCEEDED,
    RUNNING,
    FAILED
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class TaskBuilder {}
}
