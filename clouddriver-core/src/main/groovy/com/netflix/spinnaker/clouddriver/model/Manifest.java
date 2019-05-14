/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface Manifest {
  Moniker getMoniker();

  String getAccount();

  String getName();

  String getLocation();

  Status getStatus();

  List<Warning> getWarnings();

  @Data
  class Status {
    Condition stable = Condition.builder().state(true).build();
    Condition paused = Condition.builder().state(false).build();
    Condition available = Condition.builder().state(true).build();
    Condition failed = Condition.builder().state(false).build();

    public Status unknown() {
      stable = null;
      failed = null;

      return this;
    }

    public Status failed(String message) {
      failed.setMessage(message);
      failed.setState(true);

      return this;
    }

    public Status unstable(String message) {
      stable.setMessage(message);
      stable.setState(false);

      return this;
    }

    public Status paused(String message) {
      paused.setMessage(message);
      paused.setState(true);

      return this;
    }

    public Status unavailable(String message) {
      available.setMessage(message);
      available.setState(false);

      return this;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Condition {
      boolean state;
      String message;
    }
  }

  @Data
  @Builder
  public static class Warning {
    private String type;
    private String message;
  }
}
