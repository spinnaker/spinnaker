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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileStatic
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED

@CompileStatic
abstract class Execution<T> implements Serializable {
  String id
  String application
  String executingInstance

  Long buildTime

  boolean canceled
  String canceledBy
  boolean parallel
  boolean limitConcurrent = false
  boolean keepWaitingPipelines = false

  final Map<String, Object> appConfig = [:]
  final Map<String, Object> context = [:]
  List<Stage<T>> stages = []

  Long startTime
  Long endTime
  ExecutionStatus status = NOT_STARTED

  AuthenticationDetails authentication
  PausedDetails paused

  /*
   * Used to track Stages/Steps as they're built to prevent unnecessary re-builds in parallel pipelines
   */
  private final Set<Object> builtPipelineObjects = []

  Stage namedStage(String type) {
    stages.find {
      it.type == type
    }
  }

  Execution<T> asImmutable() {
    def self = this

    new Execution<T>() {
      @Override
      String getId() {
        self.id
      }

      @Override
      void setId(String id) {

      }

      @Override
      List<Stage> getStages() {
        ImmutableList.copyOf(self.stages)
      }

      @Override
      void setStages(List<Stage<T>> stages) {

      }

      @Override
      Stage namedStage(String type) {
        self.namedStage(type).asImmutable()
      }

      @Override
      Execution asImmutable() {
        this
      }

      String getExecutingInstance() {
        self.executingInstance
      }
    }
  }

  @JsonIgnore
  Set<Object> getBuiltPipelineObjects() {
    return builtPipelineObjects
  }

  static class AuthenticationDetails implements Serializable {
    String user
    Collection<String> allowedAccounts

    static Optional<AuthenticationDetails> build() {
      def spinnakerUserOptional = AuthenticatedRequest.getSpinnakerUser()
      def spinnakerAccountsOptional = AuthenticatedRequest.getSpinnakerAccounts()
      if (spinnakerUserOptional.present || spinnakerAccountsOptional.present) {
        return Optional.of(new AuthenticationDetails(
          user: spinnakerUserOptional.orElse(null),
          allowedAccounts: spinnakerAccountsOptional.present ? spinnakerAccountsOptional.get().split(
            ",") as Collection<String> : null
        ))
      }

      return Optional.empty()
    }
  }

  static class PausedDetails implements Serializable {
    String pausedBy
    String resumedBy

    Long pauseTime
    Long resumeTime

    @JsonIgnore
    boolean isPaused() {
      return pauseTime != null && resumeTime == null
    }

    @JsonIgnore
    long getPausedMs() {
      return (pauseTime != null && resumeTime != null) ? resumeTime - pauseTime : 0
    }
  }
}
