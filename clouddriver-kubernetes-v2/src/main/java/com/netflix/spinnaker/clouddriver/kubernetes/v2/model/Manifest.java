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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.model;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

public interface Manifest {
  Moniker getMoniker();

  String getAccount();

  String getName();

  String getLocation();

  Status getStatus();

  List<Warning> getWarnings();

  @Getter
  @EqualsAndHashCode
  @NonnullByDefault
  @ToString
  class Status {
    private @Nullable Condition stable = Condition.withState(true);
    private Condition paused = Condition.withState(false);
    private Condition available = Condition.withState(true);
    private @Nullable Condition failed = Condition.withState(false);

    public static Status defaultStatus() {
      return new Status();
    }

    public Status unknown() {
      stable = null;
      failed = null;
      return this;
    }

    public Status failed(@Nullable String message) {
      failed = new Condition(true, message);
      return this;
    }

    public Status stable(@Nullable String message) {
      stable = new Condition(true, message);
      return this;
    }

    public Status unstable(@Nullable String message) {
      stable = new Condition(false, message);
      return this;
    }

    public Status paused(@Nullable String message) {
      paused = new Condition(true, message);
      return this;
    }

    public Status unavailable(@Nullable String message) {
      available = new Condition(false, message);
      return this;
    }

    @NonnullByDefault
    @Value
    public static final class Condition {
      private static final Condition TRUE = new Condition(true, null);
      private static final Condition FALSE = new Condition(false, null);

      private final boolean state;
      @Nullable private final String message;

      private static Condition withState(boolean state) {
        return state ? TRUE : FALSE;
      }

      private Condition(boolean state, @Nullable String message) {
        this.state = state;
        this.message = message;
      }
    }
  }

  @Data
  @Builder
  public static class Warning {
    private String type;
    private String message;
  }
}
