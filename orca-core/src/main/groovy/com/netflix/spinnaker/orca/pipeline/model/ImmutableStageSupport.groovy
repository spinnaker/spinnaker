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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.ExecutionStatus

import java.util.concurrent.atomic.AtomicInteger

class ImmutableStageSupport {

  static def <T extends Stage> T toImmutable(T stage) {
    (T) new ImmutableStage(stage)
  }

  static class ImmutableStage<T extends Execution> implements Stage<T> {
    final Stage<T> self
    final boolean immutable = true

    public ImmutableStage(Stage<T> self) {
      this.self = self
    }

    @Override
    String getRefId() {
      self.refId
    }

    @Override
    void setRefId(String refId) {
      fail()
    }

    @Override
    String getId() {
      self.id
    }

    @Override
    String getType() {
      self.type
    }

    @Override
    String getName() {
      self.name
    }

    @Override
    Execution<T> getExecution() {
      self.execution
    }

    @Override
    Long getStartTime() {
      self.startTime
    }

    @Override
    Long getEndTime() {
      self.endTime
    }

    @Override
    void setStartTime(Long startTime) {

    }

    @Override
    void setEndTime(Long endTime) {

    }

    @Override
    ExecutionStatus getStatus() {
      self.status
    }

    @Override
    void setStatus(ExecutionStatus status) {

    }

    boolean equals(o) {
      return self.equals(o)
    }

    int hashCode() {
      return self.hashCode()
    }

    @Override
    Stage preceding(String type) {
      self.preceding(type)?.asImmutable()
    }

    @Override
    ImmutableMap<String, Object> getContext() {
      ImmutableMap.copyOf(self.context ?: [:])
    }

    @Override
    boolean isInitializationStage() {
      self.initializationStage
    }

    @Override
    void setInitializationStage(boolean initializationStage) {
      self.initializationStage = initializationStage
    }

    @Override
    AtomicInteger getTaskCounter() {
      self.taskCounter
    }

    @Override
    Stage<T> asImmutable() {
      this
    }

    @Override
    List<Task> getTasks() {
      ImmutableList.copyOf(self.tasks)
    }

    @Override
    def <O> O mapTo(Class<O> type) {
      self.mapTo(type)
    }

    @Override
    def <O> O mapTo(String pointer, Class<O> type) {
      self.mapTo(pointer, type)
    }

    @Override
    void commit(Object obj) {
      fail()
    }

    @Override
    void commit(String pointer, Object obj) {
      fail()
    }

    @Override
    Stage.SyntheticStageOwner getSyntheticStageOwner() {
      self.syntheticStageOwner
    }

    @Override
    void setSyntheticStageOwner(Stage.SyntheticStageOwner syntheticStageOwner) {
      fail()
    }

    @Override
    List<InjectedStageConfiguration> getBeforeStages() {
      ImmutableList.of(self.beforeStages)
    }

    @Override
    List<InjectedStageConfiguration> getAfterStages() {
      ImmutableList.of(self.afterStages)
    }

    @Override
    String getParentStageId() {
      self.parentStageId
    }

    @Override
    void setParentStageId(String id) {
      fail()
    }

    @Override
    Collection<String> getRequisiteStageRefIds() {
      ImmutableList.of(self.requisiteStageRefIds)
    }

    @Override
    void setRequisiteStageRefIds(Collection<String> requisiteStageRefIds) {
      fail()
    }

    long getScheduledTime() {
      this.scheduledTime
    }

    void setScheduledTime(long scheduledTime) {
      self.scheduledTime = scheduledTime  // This is needed here as the scheduledTime is set in the task
    }

    private static void fail() {
      throw new IllegalStateException("Stage is currently immutable")
    }

    Stage<T> unwrap() {
      self
    }

    @Override
    public String toString() {
      self.toString()
    }
  }
}
