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

package com.netflix.spinnaker.igor.concourse.client.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.Setter;

@Data
public class Plan {
  private InnerPlan plan;

  @Data
  public static class InnerPlan {
    @JsonAlias("do")
    private List<Op> does;
  }

  @Setter
  public static class Op {
    private String id;

    @Nullable private ResourceShape get;

    @Nullable private ResourceShape put;

    @Nullable private OnSuccess onSuccess;

    @Nullable
    public Resource getResource() {
      String resourceId = id;
      ResourceShape shape = get;
      if (shape == null && onSuccess != null) {
        shape = onSuccess.getStep().put;
        resourceId = onSuccess.getStep().id;
      }
      if (shape == null) {
        return null;
      }
      return new Resource(resourceId, shape.getName(), shape.getType());
    }
  }

  @Data
  private static class ResourceShape {
    private String type;
    private String name;
  }

  @Data
  private static class OnSuccess {
    private Op step;
  }

  public List<Resource> getResources() {
    return plan.getDoes().stream()
        .map(Op::getResource)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
