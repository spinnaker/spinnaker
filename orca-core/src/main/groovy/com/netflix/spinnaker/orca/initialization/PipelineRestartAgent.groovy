/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.initialization

import java.time.Clock
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationHandler
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import net.greghaines.jesque.client.Client
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable
import static java.util.concurrent.TimeUnit.MINUTES

@Component
@Slf4j
@CompileStatic
class PipelineRestartAgent extends AbstractPollingNotificationAgent {

  public static final String NOTIFICATION_TYPE = "stalePipeline"

  private final Clock clock
  private final Duration minInactivity
  private final JobRepository jobRepository
  private final JobExplorer jobExplorer
  private final ExecutionRepository executionRepository

  @Autowired
  PipelineRestartAgent(ObjectMapper mapper, Client jesqueClient, Clock clock, Duration minInactivity, JobRepository jobRepository, JobExplorer jobExplorer, ExecutionRepository executionRepository) {
    super(mapper, jesqueClient)
    this.jobExplorer = jobExplorer
    this.executionRepository = executionRepository
    this.jobRepository = jobRepository
    this.minInactivity = minInactivity
    this.clock = clock
  }

  @Override
  long getPollingInterval() {
    return MINUTES.toSeconds(2)
  }

  @Override
  String getNotificationType() {
    NOTIFICATION_TYPE
  }

  @Override
  @CompileDynamic
  protected Observable<Execution> getEvents() {
    log.info("Starting stale pipelines polling")
    Observable.from(jobExplorer.getJobNames())
              .flatMapIterable(jobExplorer.&findRunningJobExecutions)
              .filter(this.&isInactive)
              .doOnNext({ log.info "found stale job $it.id" })
//              .doOnNext(this.&resetExecution)
              .map(this.&executionToPipeline)
    .doOnNext({log.info "would restart pipeline ${it?.id}" })
              .filter({ it != null })
              .doOnCompleted({
      log.info("Finished stale pipelines polling")
    })
  }

  @Override
  Class<? extends NotificationHandler> handlerType() {
    PipelineRestartHandler
  }

  private boolean isInactive(JobExecution execution) {
    def cutoff = clock.instant().minus(minInactivity)
    def mostRecentUpdate = execution.stepExecutions*.lastUpdated?.max()?.toInstant()
    return mostRecentUpdate?.isBefore(cutoff.plusMillis(1))
  }

  /**
   * Because "restartability" of a Spring Batch job relies on it having been cleanly stopped and we can't guarantee
   * that we need to update the job to a STOPPED state.
   */
  private void resetExecution(JobExecution execution) {
    execution.setExitStatus(ExitStatus.STOPPED.addExitDescription("restarted after instance shutdown"))
    execution.setStatus(BatchStatus.STOPPED)
    execution.setEndTime(new Date())
    jobRepository.update(execution)
  }

  private Pipeline executionToPipeline(JobExecution execution) {
    def id = execution.getJobParameters().getString("pipeline")
    try {
      return executionRepository.retrievePipeline(id)
    } catch (Exception e) {
      log.error("Failed to retrieve pipeline $id", e)
      return null
    }
  }
}
