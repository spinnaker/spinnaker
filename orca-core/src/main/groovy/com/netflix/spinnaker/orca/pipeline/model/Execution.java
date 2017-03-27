/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.io.Serializable;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import lombok.Data;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static java.util.Arrays.asList;

@Data
public abstract class Execution<T extends Execution<T>> implements Serializable {

  String id;
  String application;
  String executingInstance;

  Long buildTime;

  boolean canceled;
  String canceledBy;
  String cancellationReason;
  boolean parallel;
  boolean limitConcurrent = false;
  boolean keepWaitingPipelines = false;

  final Map<String, Object> appConfig = new HashMap<>();
  final Map<String, Object> context = new HashMap<>();
  List<Stage<T>> stages = new ArrayList<>();

  Long startTime;
  Long endTime;
  ExecutionStatus status = NOT_STARTED;

  AuthenticationDetails authentication;
  PausedDetails paused;

  public Stage namedStage(String type) {
    return stages
      .stream()
      .filter(it -> it.getType().equals(type))
      .findFirst()
      .orElse(null);
  }

  @Data
  public static class AuthenticationDetails implements Serializable {

    String user;
    Collection<String> allowedAccounts = new ArrayList<>();

    public AuthenticationDetails() {}

    public AuthenticationDetails(String user, String... allowedAccounts) {
      this.user = user;
      this.allowedAccounts = asList(allowedAccounts);
    }

    public static Optional<AuthenticationDetails> build() {
      Optional<String> spinnakerUserOptional = AuthenticatedRequest.getSpinnakerUser();
      Optional<String> spinnakerAccountsOptional = AuthenticatedRequest.getSpinnakerAccounts();
      if (spinnakerUserOptional.isPresent() || spinnakerAccountsOptional.isPresent()) {
        return Optional.of(new AuthenticationDetails(
          spinnakerUserOptional.orElse(null),
          spinnakerAccountsOptional.map(s -> s.split(",")).orElse(null)
        ));
      }

      return Optional.empty();
    }

    public Optional<User> toKorkUser() {
      return Optional
        .ofNullable(user)
        .map(it -> {
          User user = new User();
          user.setEmail(it);
          user.setAllowedAccounts(allowedAccounts);
          return user;
        });
    }
  }

  @Data
  public static class PausedDetails implements Serializable {
    String pausedBy;
    String resumedBy;

    Long pauseTime;
    Long resumeTime;

    @JsonIgnore
    public boolean isPaused() {
      return pauseTime != null && resumeTime == null;
    }

    @JsonIgnore
    public long getPausedMs() {
      return (pauseTime != null && resumeTime != null) ? resumeTime - pauseTime : 0;
    }
  }
}
