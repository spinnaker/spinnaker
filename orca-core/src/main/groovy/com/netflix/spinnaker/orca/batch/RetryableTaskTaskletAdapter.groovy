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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.RetryableTask
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.retry.backoff.FixedBackOffPolicy
import org.springframework.retry.policy.SoftReferenceMapRetryContextCache
import org.springframework.retry.policy.TimeoutRetryPolicy
import org.springframework.retry.support.RetryTemplate

class RetryableTaskTaskletAdapter extends TaskTaskletAdapter {

  final RetryTemplate retryTemplate

  protected RetryableTaskTaskletAdapter(RetryableTask task) {
    super(task)
    retryTemplate = new RetryTemplate()
    retryTemplate.retryContextCache = new SoftReferenceMapRetryContextCache()
    retryTemplate.backOffPolicy = new FixedBackOffPolicy(backOffPeriod: task.backoffPeriod)
    retryTemplate.retryPolicy = new TimeoutRetryPolicy(timeout: task.timeout)
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
    retryTemplate.execute {
      def status = super.execute(contribution, chunkContext)
      if (status.continuable) {
        throw new RuntimeException()
      } else {
        return status
      }
    }
  }
}
