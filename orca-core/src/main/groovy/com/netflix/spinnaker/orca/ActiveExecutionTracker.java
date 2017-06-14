/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca;

import java.util.Map;
import java.util.SortedSet;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Value;
import org.apache.commons.lang.builder.CompareToBuilder;
import static java.lang.String.format;

@Deprecated
public interface ActiveExecutionTracker {
  Map<String, OrcaInstance> activeExecutionsByInstance();

  boolean isActiveInstance(String instance);

  @Value class OrcaInstance {
    boolean overdue;
    int count;
    SortedSet<ExecutionRecord> executions;
  }

  @Value class ExecutionRecord implements Comparable<ExecutionRecord> {
    String application;
    @JsonIgnore String type;
    @JsonIgnore String id;

    public String getURL() {
      if (type.equals("task")) {
        return format("/tasks/%s", id);
      } else {
        return format("/pipelines/%s", id);
      }
    }

    @Override
    public String toString() {
      return format("%s:%s:%s", application, type, id);
    }

    public static ExecutionRecord valueOf(String s) {
      String[] split = s.split(":");
      if (split.length != 3) {
        throw new IllegalArgumentException();
      }
      return new ExecutionRecord(split[0], split[1], split[2]);
    }

    @Override public int compareTo(ExecutionRecord o) {
      return new CompareToBuilder()
        .append(application, o.application)
        .append(type, o.type)
        .append(id, o.id)
        .toComparison();
    }
  }
}
