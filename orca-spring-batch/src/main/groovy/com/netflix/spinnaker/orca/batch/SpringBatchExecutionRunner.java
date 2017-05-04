/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.listeners.StageListener;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunnerSupport;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.*;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine;
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.FlowJobBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.TaskletStepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import static com.google.common.collect.Iterables.indexOf;
import static com.google.common.collect.Iterables.toArray;
import static com.google.common.collect.Maps.newHashMap;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.ExecutionStatus.REDIRECT;
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

@Component
@Slf4j
public class SpringBatchExecutionRunner extends ExecutionRunnerSupport {

  private static final int MAX_PARALLEL_CONCURRENCY = 25;

  private final ExecutionRepository executionRepository;
  private final JobLauncher jobLauncher;
  private final JobRegistry jobRegistry;
  private final JobOperator jobOperator;
  private final JobRepository jobRepository;
  private final JobBuilderFactory jobs;
  private final StepBuilderFactory steps;
  private final TaskTaskletAdapter taskTaskletAdapter;
  private final Collection<com.netflix.spinnaker.orca.Task> tasks;
  private final ExecutionListenerProvider executionListenerProvider;

  @Autowired
  public SpringBatchExecutionRunner(
    Collection<StageDefinitionBuilder> stageDefinitionBuilders,
    ExecutionRepository executionRepository,
    JobLauncher jobLauncher,
    JobRegistry jobRegistry,
    JobOperator jobOperator,
    JobRepository jobRepository,
    JobBuilderFactory jobs,
    StepBuilderFactory steps,
    TaskTaskletAdapter taskTaskletAdapter,
    Collection<com.netflix.spinnaker.orca.Task> tasks,
    ExecutionListenerProvider executionListenerProvider
  ) {
    super(stageDefinitionBuilders);
    this.executionRepository = executionRepository;
    this.jobLauncher = jobLauncher;
    this.jobRegistry = jobRegistry;
    this.jobOperator = jobOperator;
    this.jobRepository = jobRepository;
    this.jobs = jobs;
    this.steps = steps;
    this.taskTaskletAdapter = taskTaskletAdapter;
    this.tasks = tasks;
    this.executionListenerProvider = executionListenerProvider;
  }

  @Override
  public <T extends Execution<T>> void start(T execution) throws JobExecutionException {
    Job job = createJob(execution);

    // TODO-AJ This is hokiepokie
    if (execution instanceof Pipeline) {
      executionRepository.store((Pipeline) execution);
    } else {
      executionRepository.store((Orchestration) execution);
    }

    jobLauncher.run(job, createJobParameters(execution));
  }

  @Override
  public <T extends Execution<T>> void restart(T execution) throws JobExecutionException {
    String name = jobNameFor(execution);
    JobParameters params = createJobParameters(execution);
    JobExecution batchExecution = jobRepository.getLastJobExecution(name, params);
    if (batchExecution == null) {
      start(execution);
    } else {
      jobOperator.restart(batchExecution.getId());
    }
  }

  @Override public ExecutionEngine engine() {
    return ExecutionEngine.v2;
  }

  private <E extends Execution<E>> JobParameters createJobParameters(E subject) {
    JobParametersBuilder params = new JobParametersBuilder();
    if (subject instanceof Pipeline) {
      params.addString("pipeline", subject.getId());
    } else if (subject instanceof Orchestration) {
      params.addString("orchestration", subject.getId());
    }
//    params.addString("name", subject.getName());
    params.addString("application", subject.getApplication());
    params.addString("timestamp", String.valueOf(System.currentTimeMillis()));
    return params.toJobParameters();
  }

  private <E extends Execution<E>> Job createJob(E execution) throws NoSuchJobException, DuplicateJobException {
    String jobName = jobNameFor(execution);
    if (!jobRegistry.getJobNames().contains(jobName)) {
      FlowJobBuilder flowJobBuilder = buildStepsForExecution(execution, jobs.get(jobName)).build();

      executionListenerProvider.allJobExecutionListeners().forEach(flowJobBuilder::listener);

      Job job = flowJobBuilder.build();
      jobRegistry.register(new ReferenceJobFactory(job));
    }
    return jobRegistry.getJob(jobName);
  }

  private <E extends Execution<E>> FlowBuilder<FlowJobBuilder> buildStepsForExecution(E execution, JobBuilder builder) {
    List<Stage<E>> stages = execution.getStages();
    FlowBuilder<FlowJobBuilder> flow = builder.flow(initStep());
    List<Stage<E>> initialStages = stages
      .stream()
      .filter(Stage::isInitialStage)
      .collect(toList());
    Set<Serializable> alreadyBuilt = new HashSet<>();
    Map<Serializable, AtomicInteger> taskIdGenerators = new HashMap<>();
    if (initialStages.size() > 1) {
      flow = buildDownstreamFork(execution.getId(), initialStages, taskIdGenerators, alreadyBuilt, flow);
    } else if (initialStages.size() == 1) {
      flow = buildStepsForStageAndDownstream(initialStages.get(0), taskIdGenerators, alreadyBuilt, flow);
    } else {
      throw new IllegalStateException("Could not identify initial stages for pipeline");
    }
    return flow;
  }

  private Step initStep() {
    return steps.get("orca-init-step")
      .tasklet(new PipelineInitializerTasklet())
      .build();
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> buildStepsForStageAndDownstream(Stage<E> stage, Map<Serializable, AtomicInteger> taskIdGenerators, Set<Serializable> alreadyBuilt, FlowBuilder<Q> flow) {
    if (alreadyBuilt.contains(stage.getRefId())) {
      log.info("Already built {}", stage.getRefId());
      return flow;
    } else {
      alreadyBuilt.add(stage.getRefId());
      if (stage.isJoin()) {
        flow.next(buildUpstreamJoin(stage, taskIdGenerators));
      } else {
        flow.next(buildStepsForStage(stage, taskIdGenerators));
      }
      return buildDownstreamStages(stage, taskIdGenerators, alreadyBuilt, flow);
    }
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> buildDownstreamStages(final Stage<E> stage, Map<Serializable, AtomicInteger> taskIdGenerators, Set<Serializable> alreadyBuilt, FlowBuilder<Q> flow) {
    List<Stage<E>> downstreamStages = stage.downstreamStages();
    boolean isFork = downstreamStages.size() > 1;
    if (isFork) {
      return buildDownstreamFork(stage.getId(), downstreamStages, taskIdGenerators, alreadyBuilt, flow);
    } else if (downstreamStages.isEmpty()) {
      return flow;
    } else {
      // TODO: loop is misleading as we've already established there is only one
      for (Stage<E> downstreamStage : downstreamStages) {
        flow = buildStepsForStageAndDownstream(downstreamStage, taskIdGenerators, alreadyBuilt, flow);
      }
      return flow;
    }
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> buildDownstreamFork(String parentId, List<Stage<E>> downstreamStages, Map<Serializable, AtomicInteger> taskIdGenerators, Set<Serializable> alreadyBuilt, FlowBuilder<Q> flow) {
    List<Flow> flows = downstreamStages
      .stream()
      .flatMap(downstreamStage -> {
        FlowBuilder<Flow> flowBuilder = flowBuilder(format("ChildExecution.%s.%s", downstreamStage.getRefId(), downstreamStage.getId()));
        flowBuilder = buildStepsForStageAndDownstream(downstreamStage, taskIdGenerators, alreadyBuilt, flowBuilder);
        if (((FlowBuilderWrapper) flowBuilder).empty) {
          /*
           * No sense building downstream flows for stages that have been previously built.
           *
           * This commonly occurs when a parent and child stage share the same requisiteRefId.
           *
           * ie) [C] depends on [A] + [B] and [B] depends on [A]
           *
           * This example would be more cleanly written as [C] depends on [B] and [B] depends on [A].
           */
          return Stream.empty();
        }

        return Stream.of(flowBuilder.build());
      })
      .collect(toList());
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
    executor.setConcurrencyLimit(MAX_PARALLEL_CONCURRENCY);
    // children of a fan-out stage should be executed in parallel
    FlowBuilder<Flow> parallelFlowBuilder = flowBuilder(format("ParallelChildren.%s", parentId));
    parallelFlowBuilder
      .start(new SimpleFlow("NoOp"))
      .split(executor)
      .add(flows.toArray(new Flow[flows.size()]));
    return flow.next(parallelFlowBuilder.build());
  }

  private <E extends Execution<E>> Flow buildUpstreamJoin(Stage<E> stage, Map<Serializable, AtomicInteger> taskIdGenerators) {
    // insert an artificial join stage that will wait for all parents to complete
    Stage<E> waitForStage = newStage(
      stage.getExecution(),
      WaitForRequisiteCompletionStage.PIPELINE_CONFIG_TYPE,
      "Wait For Parent Tasks",
      newHashMap(singletonMap("requisiteIds", stage.getRequisiteStageRefIds())),
      null,
      null
    );
    waitForStage.setId(format("%s-waitForRequisite", stage.getId()));
    waitForStage.setRequisiteStageRefIds(emptySet());

    // 'this' stage should be added after the join stage
    FlowBuilder<Flow> waitForFlow = flowBuilder(format("WaitForRequisite.%s.%s", stage.getRefId(), stage.getId()));

    planStage(waitForStage, (them, taskGraph) -> {
      // inject join stage into execution
      List<Stage<E>> stages = stage.getExecution().getStages();
      int stageIdx = stages.indexOf(stage);
      stages.add(stageIdx, waitForStage);
      addStepsToFlow(waitForStage, taskGraph, taskIdGenerators, waitForFlow);
      // TODO: single callback would make more sense
    });

    return waitForFlow.next(buildStepsForStage(stage, taskIdGenerators)).build();
  }

  private <E extends Execution<E>> Flow buildStepsForStage(Stage<E> stage, Map<Serializable, AtomicInteger> taskIdGenerators) {
    final FlowBuilder<Flow> subFlow = flowBuilder(format("ChildExecution.%s.%s", stage.getRefId(), stage.getId()));
    buildStepsForFlow(stage, taskIdGenerators, subFlow);
    return subFlow.build();
  }

  private <E extends Execution<E>> void buildStepsForFlow(Stage<E> stage, Map<Serializable, AtomicInteger> taskIdGenerators, FlowBuilder<Flow> flow) {
    planBeforeOrAfterStages(stage, STAGE_BEFORE, (preStage) ->
      buildStepsForFlow(preStage, taskIdGenerators, flow)
    );
    planStage(
      stage,
      (stages, taskGraph) -> {
        Stage<E> firstStage = stages.iterator().next();
        boolean needsPlanning = !firstStage.getType().equals(stage.getType());
        if (stages.size() > 1) {
          // TODO: this is just ignoring the taskGraph
          addParallelStepsToFlow(stages, taskGraph, taskIdGenerators, needsPlanning, flow);
        } else if (stages.size() == 1) {
          // TODO: if this is a parallel stage with a strategy stage the taskGraph is incomplete
          if (needsPlanning) {
            buildStepsForFlow(firstStage, taskIdGenerators, flow);
          } else {
            addStepsToFlow(firstStage, taskGraph, taskIdGenerators, flow);
          }
        }
      }
    );
    planBeforeOrAfterStages(stage, STAGE_AFTER, (postStage) ->
      buildStepsForFlow(postStage, taskIdGenerators, flow)
    );
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> addStepsToFlow(Stage<E> stage, TaskNode.TaskGraph taskGraph, Map<Serializable, AtomicInteger> taskIdGenerators, FlowBuilder<Q> flow) {
    // TODO: I'm sure there's a better way to handle this
    AtomicReference<Step> loopStart = new AtomicReference<>();
    AtomicReference<Step> loopEnd = new AtomicReference<>();

    AtomicInteger idGenerator = taskIdGenerators.computeIfAbsent(stage.getId(), key -> new AtomicInteger());

    planTasks(stage, taskGraph, () -> String.valueOf(idGenerator.incrementAndGet()), task -> {
      Step step = buildStepForTask(stage, task);
      if (task.isLoopStart()) {
        loopStart.set(step);
      }
      if (task.isLoopEnd()) {
        loopEnd.set(step);
      }
      flow.next(step);
      if (task.isLoopEnd()) {
        flow
          .on(REDIRECT.name())
          .to(loopStart.get())
          .from(loopEnd.get());
      }
    });

    return flow;
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> addParallelStepsToFlow(Collection<Stage<E>> stages, TaskNode.TaskGraph taskGraph, Map<Serializable, AtomicInteger> taskIdGenerators, boolean needsPlanning, FlowBuilder<Q> flow) {
    List<Flow> flows = stages
      .stream()
      .map(stage -> {
        FlowBuilder<Flow> branchFlow = new FlowBuilderWrapper<>(stage.getName());
        if (!needsPlanning) {
          addStepsToFlow(stage, taskGraph, taskIdGenerators, branchFlow);
        } else {
          buildStepsForFlow(stage, taskIdGenerators, branchFlow);
        }
        return branchFlow.build();
      })
      .collect(toList());

    FlowBuilder<Flow> parallelFlowBuilder = new FlowBuilderWrapper<>(format("ParallelStage.%s", UUID.randomUUID()));
    // bug in Spring Batch means we have to start with a no-op step here
    // otherwise the first parallel won't actually run.
    // See https://jira.spring.io/browse/BATCH-2346 is available
    parallelFlowBuilder
      .start(new SimpleFlow("NoOp"))
      .split(new SimpleAsyncTaskExecutor())
      .add(toArray(flows, Flow.class));

    return flow.next(parallelFlowBuilder.build());
  }

  private <E extends Execution<E>> Step buildStepForTask(Stage<E> stage, Task task) {
    TaskletStepBuilder stepBuilder = steps
      .get(stepName(stage, task))
      .tasklet(buildTaskletForTask(task));

    executionListenerProvider
      .allStepExecutionListeners()
      .forEach(stepBuilder::listener);

    if (task.isLoopEnd()) {
      stepBuilder.listener(executionListenerProvider.wrap(new LoopResetListener()));
    }

    return stepBuilder.build();
  }

  private <E extends Execution<E>> String stepName(Stage<E> stage, Task task) {
    return format("%s.%s.%s.%s", stage.getId(), stage.getType(), task.getName(), task.getId());
  }

  private Tasklet buildTaskletForTask(Task task) {
    try {
      Class<? extends com.netflix.spinnaker.orca.Task> type = (Class<? extends com.netflix.spinnaker.orca.Task>) Class.forName(task.getImplementingClass());
      return tasks
        .stream()
        .filter(it -> type.isAssignableFrom(it.getClass()))
        .findFirst()
        .map(taskTaskletAdapter::decorate)
        .orElseThrow(() -> new IllegalStateException(format("No Task implementing %s found", type.getName())));
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(format("Task implementation %s not found", task.getImplementingClass()));
    }
  }

  private <E extends Execution> String jobNameFor(E execution) {
    return format("%s:%s:%s", execution.getClass().getSimpleName(), execution.getApplication(), execution.getId());
  }

  private static <Q> FlowBuilder<Q> flowBuilder(String name) {
    return new FlowBuilderWrapper<>(name);
  }

  /**
   * This just extends {@link FlowBuilder} to allow you to call {@link #next}
   * all the time instead of having to remember to call {@link #from} the first
   * time.
   */
  private static class FlowBuilderWrapper<Q> extends FlowBuilder<Q> {

    private boolean empty = true;

    FlowBuilderWrapper(String name) {
      super(name);
    }

    @Override public FlowBuilderWrapper<Q> next(Flow flow) {
      if (empty) {
        from(flow);
        empty = false;
      } else {
        super.next(flow);
      }
      return this;
    }

    @Override public FlowBuilderWrapper<Q> next(Step step) {
      if (empty) {
        from(step);
        empty = false;
      } else {
        super.next(step);
      }
      return this;
    }
  }

  private static class LoopResetListener implements StageListener {
    @Override
    public <T extends Execution<T>> void afterTask(Persister persister, Stage<T> stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
      if (executionStatus == REDIRECT) {
        List<Task> tasks = stage.getTasks();
        int startIndex = indexOf(tasks, Task::isLoopStart);
        int endIndex = indexOf(tasks, Task::isLoopEnd);
        tasks
          .subList(startIndex, endIndex + 1)
          .forEach(t -> {
            log.info(format("Resetting task %s for repeat of loop", t.getName()));
            t.setStatus(NOT_STARTED);
            t.setEndTime(null);
          });
        persister.save(stage);
      }
    }
  }
}
