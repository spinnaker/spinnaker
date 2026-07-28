/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class InMemoryExecutionRepositoryTest {

  private final InMemoryExecutionRepository repository = new InMemoryExecutionRepository();

  // InMemoryExecutionRepository doesn't sort retrievePipelinesForPipelineConfigId results, so
  // these tests only assert what the paging contract actually promises: distinct, non-overlapping
  // pages that together cover every match, and no exception once paging runs past the available
  // results.

  @Test
  void returnsDistinctNonOverlappingPagesInsteadOfRepeatingTheSamePage() {
    PipelineExecution first = pipeline();
    first.setStartTime(1000L);
    PipelineExecution second = pipeline();
    second.setStartTime(2000L);

    repository.store(first);
    repository.store(second);

    List<PipelineExecution> page1 = retrievePage(1);
    List<PipelineExecution> page2 = retrievePage(2);

    assertThat(page1).hasSize(1);
    assertThat(page2).hasSize(1);
    assertThat(page1.get(0).getId()).isNotEqualTo(page2.get(0).getId());
    assertThat(List.of(page1.get(0).getId(), page2.get(0).getId()))
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  @Test
  void returnsAnEmptyPageInsteadOfThrowingWhenPagingPastTheAvailableResults() {
    PipelineExecution only = pipeline();
    only.setStartTime(1000L);

    repository.store(only);

    List<PipelineExecution> page2 = retrievePage(2);

    assertThat(page2).isEmpty();
  }

  private List<PipelineExecution> retrievePage(int page) {
    ExecutionCriteria criteria =
        new ExecutionCriteria()
            .setPageSize(1)
            .setPage(page)
            .setStatuses(List.of("SUCCEEDED"))
            .setStartTimeCutoff(Instant.EPOCH);
    return repository
        .retrievePipelinesForPipelineConfigId("pipeline-1", criteria)
        .subscribeOn(Schedulers.io())
        .toList()
        .blockingGet();
  }

  private static PipelineExecution pipeline() {
    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(ExecutionType.PIPELINE, "covfefe");
    pipeline.setStatus(SUCCEEDED);
    pipeline.setPipelineConfigId("pipeline-1");
    return pipeline;
  }
}
