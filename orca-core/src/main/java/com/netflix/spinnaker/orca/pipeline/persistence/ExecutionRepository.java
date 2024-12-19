/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipeline.persistence;

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.kork.annotations.Metered;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import rx.Observable;

public interface ExecutionRepository {
  void store(@Nonnull PipelineExecution execution);

  void storeStage(@Nonnull StageExecution stage);

  void updateStageContext(@Nonnull StageExecution stage);

  void removeStage(@Nonnull PipelineExecution execution, @Nonnull String stageId);

  void addStage(@Nonnull StageExecution stage);

  @Metered(metricName = "cancelNullReason")
  void cancel(@Nonnull ExecutionType type, @Nonnull String id);

  void cancel(
      @Nonnull ExecutionType type,
      @Nonnull String id,
      @Nullable String user,
      @Nullable String reason);

  void pause(@Nonnull ExecutionType type, @Nonnull String id, @Nullable String user);

  @Metered(metricName = "resumeNoIgnore")
  void resume(@Nonnull ExecutionType type, @Nonnull String id, @Nullable String user);

  void resume(
      @Nonnull ExecutionType type,
      @Nonnull String id,
      @Nullable String user,
      boolean ignoreCurrentStatus);

  boolean isCanceled(ExecutionType type, @Nonnull String id);

  default void updateStatus(@Nonnull PipelineExecution execution) {
    updateStatus(execution.getType(), execution.getId(), execution.getStatus());
  }

  void updateStatus(ExecutionType type, @Nonnull String id, @Nonnull ExecutionStatus status);

  @Nonnull
  @Metered(metricName = "retrieveById")
  PipelineExecution retrieve(@Nonnull ExecutionType type, @Nonnull String id)
      throws ExecutionNotFoundException;

  void delete(@Nonnull ExecutionType type, @Nonnull String id);

  void delete(@Nonnull ExecutionType type, @Nonnull List<String> idsToDelete);

  @Nonnull
  @Metered(metricName = "retrieveByType")
  Observable<PipelineExecution> retrieve(@Nonnull ExecutionType type);

  @Nonnull
  @Metered(metricName = "retrieveByCriteria")
  Observable<PipelineExecution> retrieve(
      @Nonnull ExecutionType type, @Nonnull ExecutionCriteria criteria);

  @Nonnull
  Observable<PipelineExecution> retrievePipelinesForApplication(@Nonnull String application);

  @Nonnull
  Observable<PipelineExecution> retrievePipelinesForPipelineConfigId(
      @Nonnull String pipelineConfigId, @Nonnull ExecutionCriteria criteria);

  @Nonnull
  Collection<String> retrievePipelineConfigIdsForApplication(@Nonnull String application);

  @Nonnull
  Collection<String> retrieveAndFilterPipelineExecutionIdsForApplication(
      @Nonnull String application,
      @Nonnull List<String> pipelineConfigIds,
      @Nonnull ExecutionCriteria criteria);

  @Nonnull
  Collection<PipelineExecution> retrievePipelineExecutionDetailsForApplication(
      @Nonnull String application,
      @Nonnull List<String> pipelineConfigIds,
      int queryTimeoutSeconds);

  /**
   * Returns executions in the time boundary. Redis impl does not respect pageSize or offset params,
   * and returns all executions. Sql impl respects these params.
   *
   * @param executionCriteria use this param to specify: if there are statuses, only those will be
   *     returned if there is a sort type that will be used to sort the results use pageSize and
   *     page to control pagination
   */
  @Nonnull
  List<PipelineExecution> retrievePipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      @Nonnull List<String> pipelineConfigIds,
      long buildTimeStartBoundary,
      long buildTimeEndBoundary,
      ExecutionCriteria executionCriteria);

  /**
   * Returns all executions in the time boundary
   *
   * @param executionCriteria if there are statuses, only those will be returned if there is a
   *     pageSize, that will be used as the page size if there is a sort type that will be used to
   *     sort the results
   */
  @Nonnull
  List<PipelineExecution> retrieveAllPipelinesForPipelineConfigIdsBetweenBuildTimeBoundary(
      @Nonnull List<String> pipelineConfigIds,
      long buildTimeStartBoundary,
      long buildTimeEndBoundary,
      ExecutionCriteria executionCriteria);

  @Deprecated // Use the non-rx interface instead
  @Nonnull
  @Metered(metricName = "retrieveOrchestrationsForApplicationSortedAsc")
  Observable<PipelineExecution> retrieveOrchestrationsForApplication(
      @Nonnull String application, @Nonnull ExecutionCriteria criteria);

  @Nonnull
  List<PipelineExecution> retrieveOrchestrationsForApplication(
      @Nonnull String application,
      @Nonnull ExecutionCriteria criteria,
      @Nullable ExecutionComparator sorter);

  @Nonnull
  PipelineExecution retrieveByCorrelationId(
      @Nonnull ExecutionType executionType, @Nonnull String correlationId)
      throws ExecutionNotFoundException;

  @Deprecated
  @Nonnull
  PipelineExecution retrieveOrchestrationForCorrelationId(@Nonnull String correlationId)
      throws ExecutionNotFoundException;

  @Deprecated
  @Nonnull
  PipelineExecution retrievePipelineForCorrelationId(@Nonnull String correlationId)
      throws ExecutionNotFoundException;

  @Nonnull
  List<PipelineExecution> retrieveBufferedExecutions();

  @Nonnull
  List<String> retrieveAllApplicationNames(@Nullable ExecutionType executionType);

  @Nonnull
  @Metered(metricName = "retrieveAllApplicationNamesWithMinExecutions")
  List<String> retrieveAllApplicationNames(
      @Nullable ExecutionType executionType, int minExecutions);

  boolean hasExecution(@Nonnull ExecutionType type, @Nonnull String id);

  List<String> retrieveAllExecutionIds(@Nonnull ExecutionType type);

  /**
   * Returns the name of the partition that this execution repository owns/handles. {@code null}
   * means that it handles any partition (in other words, this execution repository is not partition
   * aware)
   */
  @Nullable
  default String getPartition() {
    return null;
  }

  default boolean handlesPartition(@Nullable String partitionOfExecution) {
    return partitionOfExecution
            == null // executions with no partition are implied to be owned by current orca
        || getPartition()
            == null // this repository is not restricted to a partition, can handle any execution
        || partitionOfExecution.equals(getPartition()); // both are set and must match
  }

  // defaulting to a no-op because in normal cases, this is a no-op for execution repositories
  // execution repositories that support foreign peers can override this to support restarting
  // foreign executions
  default void restartStage(String executionId, String stageId) {}

  final class ExecutionCriteria {
    private int pageSize = 3500;
    private Collection<ExecutionStatus> statuses = new ArrayList<>();
    private int page;
    private Instant startTimeCutoff;
    private ExecutionComparator sortType;

    public int getPageSize() {
      return pageSize;
    }

    public @Nonnull ExecutionCriteria setPageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    public @Nonnull Collection<ExecutionStatus> getStatuses() {
      return statuses;
    }

    public @Nonnull ExecutionCriteria setStatuses(Collection<String> statuses) {
      return setStatuses(
          statuses.stream()
              .map(it -> ExecutionStatus.valueOf(it.toUpperCase()))
              .collect(toList())
              .toArray(new ExecutionStatus[statuses.size()]));
    }

    public @Nonnull ExecutionCriteria setStatuses(ExecutionStatus... statuses) {
      this.statuses = Arrays.asList(statuses);
      return this;
    }

    public int getPage() {
      return Math.max(page, 1);
    }

    public ExecutionCriteria setPage(int page) {
      this.page = page;
      return this;
    }

    public @Nullable Instant getStartTimeCutoff() {
      return startTimeCutoff;
    }

    public ExecutionCriteria setStartTimeCutoff(Instant startTimeCutoff) {
      this.startTimeCutoff = startTimeCutoff;
      return this;
    }

    public ExecutionComparator getSortType() {
      return sortType;
    }

    public ExecutionCriteria setSortType(ExecutionComparator sortType) {
      this.sortType = sortType;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ExecutionCriteria that = (ExecutionCriteria) o;
      return pageSize == that.pageSize
          && Objects.equals(statuses, that.statuses)
          && page == that.page;
    }

    @Override
    public int hashCode() {
      return Objects.hash(pageSize, statuses, page);
    }
  }

  enum ExecutionComparator implements Comparator<PipelineExecution> {
    NATURAL_ASC {
      @Override
      public int compare(PipelineExecution a, PipelineExecution b) {
        return b.getId().compareTo(a.getId());
      }
    },

    NATURAL_DESC {
      @Override
      public int compare(PipelineExecution a, PipelineExecution b) {
        return a.getId().compareTo(b.getId());
      }
    },

    /**
     * Sort executions nulls first, then by startTime descending, breaking ties by lexicographically
     * descending IDs.
     */
    START_TIME_OR_ID {
      @Override
      public int compare(PipelineExecution a, PipelineExecution b) {
        Long aStartTime = a.getStartTime();
        Long bStartTime = b.getStartTime();

        int startCompare;
        if (aStartTime == null) {
          startCompare = bStartTime == null ? 0 : -1;
        } else if (bStartTime == null) {
          startCompare = 1;
        } else {
          startCompare = bStartTime.compareTo(aStartTime);
        }

        if (startCompare == 0) {
          return b.getId().compareTo(a.getId());
        }
        return startCompare;
      }
    },

    BUILD_TIME_DESC {
      @Override
      public int compare(PipelineExecution a, PipelineExecution b) {
        Long aBuildTime = Optional.ofNullable(a.getBuildTime()).orElse(0L);
        Long bBuildTime = Optional.ofNullable(b.getBuildTime()).orElse(0L);

        int buildCompare = bBuildTime.compareTo(aBuildTime);
        if (buildCompare == 0) {
          return b.getId().compareTo(a.getId());
        }
        return buildCompare;
      }
    },

    BUILD_TIME_ASC {
      @Override
      public int compare(PipelineExecution a, PipelineExecution b) {
        Long aBuildTime = Optional.ofNullable(a.getBuildTime()).orElse(0L);
        Long bBuildTime = Optional.ofNullable(b.getBuildTime()).orElse(0L);

        int buildCompare = aBuildTime.compareTo(bBuildTime);
        if (buildCompare == 0) {
          return a.getId().compareTo(b.getId());
        }
        return buildCompare;
      }
    };
  }
}
