/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.adapters.RetryableTaskTasklet
import com.netflix.spinnaker.orca.batch.adapters.TaskTasklet
import com.netflix.spinnaker.orca.batch.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.batch.retry.PollingRetryPolicy
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.aop.framework.ProxyFactory
import org.springframework.aop.target.SingletonTargetSource
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.retry.backoff.FixedBackOffPolicy
import org.springframework.retry.backoff.Sleeper
import org.springframework.retry.backoff.ThreadWaitSleeper
import static org.springframework.retry.interceptor.RetryInterceptorBuilder.stateless

interface TaskTaskletAdapter {
  Tasklet decorate(Task task)

  boolean akkaEnabled()
}

@CompileStatic
class TaskTaskletAdapterImpl implements TaskTaskletAdapter {

  private static final Sleeper DEFAULT_SLEEPER = new ThreadWaitSleeper()

  private final ExecutionRepository executionRepository
  private final List<ExceptionHandler> exceptionHandlers
  private final StageNavigator stageNavigator
  private final Registry registry
  private final Sleeper sleeper

  @Autowired
  TaskTaskletAdapterImpl(ExecutionRepository executionRepository,
                         List<ExceptionHandler> exceptionHandlers,
                         StageNavigator stageNavigator,
                         Registry registry = new NoopRegistry(),
                         Sleeper sleeper = DEFAULT_SLEEPER) {
    this.executionRepository = executionRepository
    this.exceptionHandlers = exceptionHandlers
    this.stageNavigator = stageNavigator
    this.registry = registry
    this.sleeper = sleeper
  }

  @Override
  Tasklet decorate(Task task) {
    if (task instanceof RetryableTask) {
      def tasklet = new RetryableTaskTasklet(task, executionRepository, exceptionHandlers, registry, stageNavigator)
      def proxyFactory = new ProxyFactory(Tasklet, new SingletonTargetSource(tasklet))
      def backOffPolicy = new FixedBackOffPolicy(
        backOffPeriod: task.backoffPeriod,
        sleeper: sleeper
      )
      proxyFactory.addAdvice(
        stateless().retryPolicy(new PollingRetryPolicy())
                   .backOffPolicy(backOffPolicy)
                   .build()
      )
      return proxyFactory.proxy as Tasklet
    } else {
      return new TaskTasklet(task, executionRepository, exceptionHandlers, registry, stageNavigator)
    }
  }

  @Override
  boolean akkaEnabled() {
    return false
  }
}
