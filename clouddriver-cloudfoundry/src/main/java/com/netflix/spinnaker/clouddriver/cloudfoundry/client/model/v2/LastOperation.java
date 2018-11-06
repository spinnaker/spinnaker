/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;

import javax.annotation.Nullable;

import static java.util.Arrays.stream;

@Data
public class LastOperation {
  private LastOperation.Type type;
  private LastOperation.State state;

  public enum Type {
    CREATE("create"),
    DELETE("delete"),
    UPDATE("update");

    private final String type;

    Type(String type) {
      this.type = type;
    }

    @Nullable
    @JsonCreator
    public static Type fromType(String type) {
      return stream(Type.values()).filter(st -> st.type.equals(type)).findFirst().orElse(null);
    }
  }

  public enum State {
    FAILED("failed"),
    IN_PROGRESS("in progress"),
    SUCCEEDED("succeeded");

    private final String state;

    State(String state) {
      this.state = state;
    }

    @Nullable
    @JsonCreator
    public static State fromState(String state) {
      return stream(State.values()).filter(st -> st.state.equals(state)).findFirst().orElse(null);
    }
  }
}
